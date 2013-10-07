package pk.qwerty12.nfclockscreenoffenabler;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XModuleResources;
import android.media.SoundPool;
import android.nfc.NfcAdapter;
import android.os.UserHandle;
import android.util.Log;
import de.robv.android.xposed.IXposedHookCmdInit;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class NFCLockScreenOffEnabler implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookCmdInit
{
	//Thanks to Tungstwenty for the preferences code, which I have taken from his Keyboard42DictInjector and made a bad job of it
	private static final String MY_PACKAGE_NAME = NFCLockScreenOffEnabler.class.getPackage().getName();
	private String MODULE_PATH;

	private SharedPreferences prefs;
	private Context mContext = null;

	private XModuleResources modRes = null;
	private SoundPool mSoundPool = null;
	private Object nfcServiceObject = null;

	// Taken from NfcService.java, Copyright (C) 2010 The Android Open Source Project, Licensed under the Apache License, Version 2.0
	// Screen state, used by mScreenState
	//private static final int SCREEN_STATE_OFF = 1;
	private static final int SCREEN_STATE_ON_LOCKED = 2;
	private static final int SCREEN_STATE_ON_UNLOCKED = 3;

	int mTagLostSound;

	private boolean mDebugMode = true;
	protected Object mViewMediatorCallback;

	private static Object mKeyguardSecurityCallbackInstance;

	private void log(String TAG, String message) {
		if (!mDebugMode)
			return;

		Log.d(TAG, message);
	}

	//Hook for NfcNativeTag$PresenceCheckWatchdog.run()
	class PresenceCheckWatchdogRunHook extends XC_MethodHook
	{
		@Override
		protected void beforeHookedMethod(MethodHookParam param)
				throws Throwable {
			{
				if (!prefs.getBoolean(Common.PREF_TAGLOST, true)) {
					return;
				}

				if (prefs.getBoolean(Common.PREF_LOCKED, true)) {
					return;
				}

				XposedHelpers.callMethod(param.thisObject, "setTimeout", prefs.getInt(Common.PREF_PRESENCE_CHECK_TIMEOUT, 2000));
			}
		}

		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable
		{
			if (!prefs.getBoolean(Common.PREF_TAGLOST, true))
				return;

			//broadcast tag lost message
			try {  
				Class<?> activityManagerNative = Class.forName("android.app.ActivityManagerNative");  

				if (activityManagerNative != null){
					Object am = activityManagerNative.getMethod("getDefault").invoke(activityManagerNative);  

					if (am != null)
						am.getClass().getMethod("resumeAppSwitches").invoke(am);
				}
			} catch (Exception e) {  
				e.printStackTrace();  
			}  

			Context context = (Context) XposedHelpers.getAdditionalInstanceField(XposedHelpers.getSurroundingThis(param.thisObject), "mContext");

			if (context == null){
				log("PresenceCheckWatchdogRunHook",  "step-4 context == null");
				return;
			}

			log("PresenceCheckWatchdogRunHook",  "step-4 context != null");
			try {
				byte[] uId = (byte[]) XposedHelpers.callMethod(XposedHelpers.getSurroundingThis(param.thisObject), "getUid");
				Intent intentToStart = new Intent();
				intentToStart.putExtra(NfcAdapter.EXTRA_ID, uId);
				intentToStart.setData(null);
				intentToStart.setType(null);
				intentToStart.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				intentToStart.setAction("android.nfc.action.TAG_LOST");

				PackageManager packageManager = context.getPackageManager();

				if (packageManager != null)
				{
					List<ResolveInfo> activities = packageManager.queryIntentActivities(intentToStart, 0);
					if (activities.size() > 0) {
						log("PresenceCheckWatchdogRunHook", 
								String.format("startActivity - android.nfc.action.TAG_LOST(%x%x%x%x)", uId[0], uId[1], uId[2], uId[3]));
						context.startActivity(intentToStart);
					}
					else{
						log("PresenceCheckWatchdogRunHook", 
								String.format("activities.size() <= 0 (%x%x%x%x)", uId[0], uId[1], uId[2], uId[3]));
					}
				}

				playTagLostSound();
			} catch (Exception e) {  
				e.printStackTrace();  
			}  
		}
	}

	//Hook for NfcService.onRemoteEndpointDiscovered(TagEndpoint tag)
	class NfcServiceOnRemoteEndpointDiscoveredHook extends XC_MethodHook
	{
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable
		{
			try {
				Object tagEndpoint = param.args[0];
				String uuid = Common.byteArrayToHexString((byte[]) XposedHelpers.callMethod(tagEndpoint, "getUid"));

				Set<String> set = new HashSet<String>();

				Set<String> authorizedNfcTags = prefs.getStringSet(Common.PREF_NFC_KEYS, set);
				Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				XposedBridge.log(uuid.trim());

				if (authorizedNfcTags != null && authorizedNfcTags.contains(uuid.trim())) {
					XposedBridge.log("Got matching NFC tag, unlocking device...");
					Intent unlockIntent = new Intent(Common.INTENT_UNLOCK_DEVICE);
					context.sendBroadcast(unlockIntent);
				}

				if (!prefs.getBoolean(Common.PREF_TAGLOST, true))
					return;


				XposedHelpers.setAdditionalInstanceField(param.args[0], "mContext", context);
				if (context != null)
					log("NfcServiceOnRemoteEndpointDiscoveredHook","setAdditionalInstanceField - mContext != null");
				else
					log("NfcServiceOnRemoteEndpointDiscoveredHook","setAdditionalInstanceField - mContext == null");
			} catch (Exception e) {  
				e.printStackTrace();  
			}  
		}
	}

	// Thanks to rovo89 for his suggested improvements: http://forum.xda-developers.com/showpost.php?p=35790508&postcount=185
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable
	{
		prefs = AndroidAppHelper.getSharedPreferencesForPackage(MY_PACKAGE_NAME, Common.PREFS, Context.MODE_PRIVATE);
		MODULE_PATH = startupParam.modulePath;
		mDebugMode = prefs.getBoolean(Common.PREF_DEBUG_MODE, true);

		try {
			Class<?> ContextImpl = findClass("android.app.ContextImpl", null);

			XC_MethodHook hook = new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					// ContextImpl extends Context
					Context context = (Context) param.thisObject;
					Intent intent = (Intent) param.args[0];
					if (intent != null && Common.INTENT_UNLOCK_DEVICE.equals(intent.getAction())) {
						if (!context.getPackageName().equals(Common.PACKAGE_NFC)) {
							param.args[0] = new Intent(Common.INTENT_UNLOCK_INTERCEPTED);
						}
					}
				}
			};

			try {
				findAndHookMethod(ContextImpl, "sendBroadcast", Intent.class, hook);
				findAndHookMethod(ContextImpl, "sendBroadcast", Intent.class, String.class, hook);
			} catch (NoSuchMethodError e) {}

			// Doesn't exist on pre-4.2
			try {
				findAndHookMethod(ContextImpl, "sendBroadcastAsUser", Intent.class, UserHandle.class, hook);
				findAndHookMethod(ContextImpl, "sendBroadcastAsUser", Intent.class, UserHandle.class, String.class, hook);
			} catch (NoSuchMethodError e) {}
		} catch (ClassNotFoundError e) {}
	}

	@Override
	public void initCmdApp(
			de.robv.android.xposed.IXposedHookCmdInit.StartupParam startupParam)
					throws Throwable {
		if (!startupParam.startClassName.equals("com.android.commands.am.Am"))
			return;

		Class<?> Am = findClass("com.android.commands.am.Am", null);
		Method sendBroadcastMethod = null;
		try {
			sendBroadcastMethod = XposedHelpers.findMethodBestMatch(Am, "makeIntent", int.class);
		} catch (NoSuchMethodError e) {
			try {
				sendBroadcastMethod = XposedHelpers.findMethodBestMatch(Am, "makeIntent");
			} catch (NoSuchMethodError e1) {}
		}

		if (sendBroadcastMethod != null) {
			XposedBridge.hookMethod(sendBroadcastMethod, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					Object result = param.getResultOrThrowable();
					if (result instanceof Intent) {
						Intent intent = (Intent) result;
						if (intent != null && Common.INTENT_UNLOCK_DEVICE.equals(intent.getAction()))
							param.setResult(new Intent(Common.INTENT_UNLOCK_INTERCEPTED));
					}
				}
			});
		}
	}

	public void playTagLostSound() {
		if (!prefs.getBoolean(Common.PREF_PLAY_TAG_LOST_SOUND, true))
			return;

		synchronized (nfcServiceObject) {
			if (mSoundPool == null) {
				Log.w("NfcService", "Not playing sound when NFC is disabled");
				return;
			}

			mSoundPool.play(mTagLostSound, 1.0f, 1.0f, 0, 0, 1.0f);
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		if (lpparam.packageName.equals(Common.PACKAGE_NFC))
		{
			modRes = XModuleResources.createInstance(MODULE_PATH, null);

			try {
				Class<?> NfcService = findClass(Common.PACKAGE_NFC + ".NfcService", lpparam.classLoader);

				// Don't reload settings on every call, that can cause slowdowns.
				// This intent is fired from NFCLockScreenOffEnablerActivity when
				// any of the parameters change.
				XposedBridge.hookAllConstructors(NfcService, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						nfcServiceObject = param.thisObject;
						mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
						mContext.registerReceiver(new BroadcastReceiver() {
							@Override
							public void onReceive(Context context, Intent intent) {
								XposedBridge.log(MY_PACKAGE_NAME + ": " + "Settings updated, reloading...");
								AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

								// This may be faster than using prefs.getBoolean, since we use this a lot.
								mDebugMode = prefs.getBoolean(Common.PREF_DEBUG_MODE, true);
							}
						}, new IntentFilter(Common.SETTINGS_UPDATED_INTENT));
					}
				});

				findAndHookMethod(NfcService, "initSoundPool", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						mSoundPool = (SoundPool) XposedHelpers.getObjectField(param.thisObject, "mSoundPool");
						synchronized (param.thisObject) {
							if (mSoundPool != null)
								mTagLostSound = mSoundPool.load(modRes.openRawResourceFd(R.raw.tag_lost), 1);
						}
					}
				});

				// Nfc module of some kinds of ROMs may call checkScreenState in applyRouting
				// and update mScreenState, so we have to hook checkScreenState and modify
				// the return value
				findAndHookMethod(NfcService, "checkScreenState", new XC_MethodHook()
				{
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable
					{
						try {
							Boolean NeedScreenOnState = (Boolean)XposedHelpers.getAdditionalInstanceField(param.thisObject, "NeedScreenOnState") ;
							if (NeedScreenOnState == null)
								return;

							if (NeedScreenOnState == false)
								return;

							param.setResult(SCREEN_STATE_ON_UNLOCKED);
						} catch (Exception e) {  
							e.printStackTrace();  
						}  
					}
				});

				findAndHookMethod(NfcService, "applyRouting", boolean.class, new XC_MethodHook()
				{
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable
					{
						try {
							final int currScreenState = (Integer) XposedHelpers.callMethod(param.thisObject, "checkScreenState");
							// We also don't need to run if the screen is already on, or if the user
							// has chosen to enable NFC on the lockscreen only and the phone is not locked
							if ((currScreenState == SCREEN_STATE_ON_UNLOCKED)
									|| (prefs.getBoolean(Common.PREF_LOCKED, true)
											&& currScreenState != SCREEN_STATE_ON_LOCKED))
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", -1);
								return;
							}

							// we are in applyRouting, set the flag NeedScreenOnState to true
							XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", true);

							synchronized (param.thisObject) // Not sure if this is correct, but NfcService.java insists on having accesses to the mScreenState variable synchronized, so I'm doing the same here
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", XposedHelpers.getIntField(param.thisObject, "mScreenState"));
								XposedHelpers.setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
							}
						} catch (Exception e) {  
							e.printStackTrace();  
						}  
					}

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable
					{
						try {
							// exit from applyRouting, set the flag NeedScreenOnState to false
							XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", false);

							final int mOrigScreenState = (Integer) XposedHelpers.getAdditionalInstanceField(param.thisObject, "mOrigScreenState");
							if (mOrigScreenState == -1)
								return;

							synchronized (param.thisObject)
							{
								// Restore original mScreenState value after applyRouting has run
								XposedHelpers.setIntField(param.thisObject, "mScreenState", mOrigScreenState);
							}
						} catch (Exception e) {  
							e.printStackTrace();  
						}  
					}
				});

				XposedHelpers.findAndHookMethod(NfcService, "onRemoteEndpointDiscovered",  
						Common.PACKAGE_NFC + ".DeviceHost$TagEndpoint",
						new NfcServiceOnRemoteEndpointDiscoveredHook());

			} catch (ClassNotFoundError e) {
				XposedBridge.log("Not hooking class .NfcService");
			}

			try {
				Class<?> PresenceCheckWatchDog = findClass(Common.PACKAGE_NFC + ".dhimpl.NativeNfcTag$PresenceCheckWatchdog", lpparam.classLoader);
				XposedHelpers.findAndHookMethod(PresenceCheckWatchDog, "run", new PresenceCheckWatchdogRunHook());
			} catch (ClassNotFoundError e) {
				XposedBridge.log("Not hooking class .dhimpl.NativeNfcTag$PresenceCheckWatchdog");
			}

			try {
				Class<?> PresenceCheckWatchdog = findClass(Common.PACKAGE_NFC +".nxp.NativeNfcTag$PresenceCheckWatchdog", lpparam.classLoader);
				XposedHelpers.findAndHookMethod(PresenceCheckWatchdog, "run", new PresenceCheckWatchdogRunHook());
			} catch (ClassNotFoundError e) {
				XposedBridge.log("Not hooking class .nxp.NativeNfcTag$PresenceCheckWatchdog");
			}
		}

		if (lpparam.packageName.equals("android")) {
			try {
				Class<?> KeyguardHostView = findClass("com.android.internal.policy.impl.keyguard.KeyguardHostView", lpparam.classLoader);
				XposedBridge.hookAllConstructors(KeyguardHostView, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						mKeyguardSecurityCallbackInstance = XposedHelpers.getObjectField(param.thisObject, "mCallback");
						Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
						context.registerReceiver(new BroadcastReceiver() {

							@Override
							public void onReceive(Context context, Intent intent) {
								if (Common.INTENT_UNLOCK_DEVICE.equals(intent.getAction())) {

									XposedBridge.log(intent.toString());

									try {
										if (mKeyguardSecurityCallbackInstance != null)
											XposedHelpers.callMethod(mKeyguardSecurityCallbackInstance, "reportSuccessfulUnlockAttempt");

										if (mViewMediatorCallback != null) {
											XposedHelpers.callMethod(mViewMediatorCallback, "keyguardDone", true);
										}
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
							}

						}, new IntentFilter(Common.INTENT_UNLOCK_DEVICE), "android.permission.INTERNAL_SYSTEM_WINDOW", null);

						context.registerReceiver(new BroadcastReceiver() {
							@Override
							public void onReceive(Context context, Intent intent) {
								Notification.Builder mBuilder =   new Notification.Builder(context)
								.setSmallIcon(android.R.drawable.ic_dialog_alert) // notification icon
								.setContentTitle("Unlock attempt intercepted") // title for notification
								.setContentText("An attempt to use an intent used by NFC unlocking to unlock your device has been prevented") // message for notification
								.setAutoCancel(true); // clear notification after click
								NotificationManager mNotificationManager =
										(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
								mNotificationManager.notify(0, mBuilder.build());
							}
						}, new IntentFilter(Common.INTENT_UNLOCK_INTERCEPTED));
					}
				});
			} catch (ClassNotFoundError e) {

			} catch (NoSuchFieldError e) {}

			try {
				Class<?> KeyguardViewManager = findClass("com.android.internal.policy.impl.keyguard.KeyguardViewManager", lpparam.classLoader);
				XposedBridge.hookAllConstructors(KeyguardViewManager, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						mViewMediatorCallback = XposedHelpers.getObjectField(param.thisObject,
								"mViewMediatorCallback");
					}
				});
			} catch (ClassNotFoundError e) {
			} catch (NoSuchFieldError e) {}
		}
	}
}

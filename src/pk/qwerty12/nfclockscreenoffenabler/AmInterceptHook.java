package pk.qwerty12.nfclockscreenoffenabler;

import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;

/* This hook prevents a potential hacker from exploiting the intent
 * we use internally to unlock the device.
 */

public class AmInterceptHook extends XC_MethodHook {
	@Override
	protected void afterHookedMethod(MethodHookParam param) throws Throwable {
		Object result = param.getResultOrThrowable();
		if (result instanceof Intent) {
			Intent intent = (Intent) result;
			if (intent != null && Common.INTENT_UNLOCK_DEVICE.equals(intent.getAction()))
				param.setResult(new Intent(Common.INTENT_UNLOCK_INTERCEPTED));
		}
	}
}

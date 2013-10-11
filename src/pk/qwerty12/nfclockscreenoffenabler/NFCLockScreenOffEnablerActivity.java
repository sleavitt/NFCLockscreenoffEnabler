package pk.qwerty12.nfclockscreenoffenabler;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class NFCLockScreenOffEnablerActivity extends PreferenceActivity {

	private CheckBoxPreference mEnableTagLostCheckBox = null;
	private CheckBoxPreference mEnableTagLostSoundCheckBox = null;
	private ListPreference mEnableNfcForStatesList = null;
	private Preference mCopyrightPreference = null;
	private EditTextPreference mPresenceCheckTimeoutPreference = null;

	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
		addPreferencesFromResource(R.xml.pref_general);
		getViews();

		final SharedPreferences prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		mEnableTagLostCheckBox.setChecked(prefs.getBoolean(Common.PREF_TAGLOST, true));
		mEnableTagLostCheckBox.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {

				boolean isChecked = (Boolean) newValue;
				mEnableTagLostSoundCheckBox.setEnabled(isChecked);

				Editor prefsEditor = prefs.edit();
				prefsEditor.putBoolean(Common.PREF_TAGLOST, isChecked);
				prefsEditor.commit();

				emitSettingsChanged();
				return true;
			}
		});

		mEnableTagLostSoundCheckBox.setEnabled(mEnableTagLostCheckBox.isChecked());
		mEnableTagLostSoundCheckBox.setChecked(prefs.getBoolean(Common.PREF_PLAY_TAG_LOST_SOUND, true));
		mEnableTagLostSoundCheckBox.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Editor prefsEditor = prefs.edit();
				prefsEditor.putBoolean(Common.PREF_PLAY_TAG_LOST_SOUND, (Boolean) newValue);
				prefsEditor.commit();

				emitSettingsChanged();
				return true;
			}
		});

		if (!prefs.getBoolean(Common.PREF_LOCKED, true))
			mEnableNfcForStatesList.setValue("screen_off");

		mEnableNfcForStatesList.setDefaultValue("locked_screen_on");
		if (!prefs.contains(Common.PREF_LOCKED))
			mEnableNfcForStatesList.setValueIndex(0);

		mEnableNfcForStatesList.setSummary("   "); // required or will not update
		mEnableNfcForStatesList.setSummary("%s");
		mEnableNfcForStatesList.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {				
				Editor prefsEditor = prefs.edit();
				prefsEditor.putBoolean(Common.PREF_LOCKED, ("locked_screen_on".equals(newValue)));
				prefsEditor.commit();

				emitSettingsChanged();

				mEnableNfcForStatesList.setSummary("   "); // required or will not update
				mEnableNfcForStatesList.setSummary("%s");
				return true;
			}
		});

		mCopyrightPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				String[] contributors = getResources().getStringArray(R.array.contributors);

				String contributorString = "";

				for (int i = 0; i < contributors.length; i++) {
					if (i != 0)
						contributorString += "\n";
					contributorString += "* " + contributors[i];
				}

				AlertDialog.Builder alertDialog = new AlertDialog.Builder(NFCLockScreenOffEnablerActivity.this)
				.setTitle(R.string.contributors_title)
				.setMessage(contributorString);

				alertDialog.show();
				return true;
			}
		});

		mPresenceCheckTimeoutPreference = (EditTextPreference) findPreference(Common.PREF_PRESENCE_CHECK_TIMEOUT);
		if (prefs.contains(Common.PREF_PRESENCE_CHECK_TIMEOUT)) {
			mPresenceCheckTimeoutPreference.setSummary(getString(R.string.pref_summary_presence_check)
					+ " " + String.valueOf(prefs.getInt(Common.PREF_PRESENCE_CHECK_TIMEOUT, 2000)));
			mPresenceCheckTimeoutPreference.setDefaultValue(String.valueOf(prefs.getInt(Common.PREF_PRESENCE_CHECK_TIMEOUT, 2000)));
		}
		mPresenceCheckTimeoutPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Editor prefsEditor = prefs.edit();
				prefsEditor.putInt(Common.PREF_PRESENCE_CHECK_TIMEOUT, Integer.parseInt((String)newValue));
				prefsEditor.commit();

				emitSettingsChanged();

				mPresenceCheckTimeoutPreference.setSummary(" ");
				mPresenceCheckTimeoutPreference.setSummary(getString(R.string.pref_summary_presence_check)
						+ " " + (String)newValue);

				mPresenceCheckTimeoutPreference.setDefaultValue((String)newValue);

				return false;
			}
		});

		findPreference(Common.PREF_DEBUG_MODE).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				emitSettingsChanged();
				return true;
			}
		});
		Preference authorizedNfcPreference = findPreference("nfc_tags");
		authorizedNfcPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(NFCLockScreenOffEnablerActivity.this, NfcTags.class));
				return false;
			}
		});
	}

	@SuppressWarnings("deprecation")
	private void getViews() {
		mEnableTagLostCheckBox = (CheckBoxPreference) findPreference(Common.PREF_TAGLOST);
		mEnableTagLostSoundCheckBox = (CheckBoxPreference) findPreference(Common.PREF_PLAY_TAG_LOST_SOUND);
		mEnableNfcForStatesList = (ListPreference) findPreference("enable_nfc_for_lock_state");
		mCopyrightPreference = (Preference) findPreference("copyright_key");
	}

	protected void emitSettingsChanged() {
		Intent i = new Intent(Common.SETTINGS_UPDATED_INTENT);
		sendBroadcast(i);
	}
}

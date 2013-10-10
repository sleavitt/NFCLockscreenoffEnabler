package pk.qwerty12.nfclockscreenoffenabler;

public class Common {
	public static final String SETTINGS_UPDATED_INTENT = "pk.qwerty12.nfclockscreenoffenabler.SETTINGS_UPDATED";

	public static final String PREFS = "NFCModSettings";
	public static final String PREF_LOCKED = "On_Locked";
	public static final String PREF_TAGLOST = "TagLostDetecting";
	public static final String PREF_PRESENCE_CHECK_TIMEOUT = "presence_check_timeout";
	public static final String PREF_DEBUG_MODE = "debug_mode";
	public static final String PREF_PLAY_TAG_LOST_SOUND = "should_play_tag_lost_sound";
	public static final String PREF_NFC_KEYS = "authorized_nfc_tag_uuids";
	public static final String PREF_NFC_KEYS_NAMES = "authorized_nfc_tag_friendly_names";

	/* -- */
	public static final String PACKAGE_NFC = "com.android.nfc";

	// Intent sent when a tag is lost
	public static final String ACTION_TAG_LOST = "android.nfc.action.TAG_LOST";

	// Intent used internally in this module to unlock the device.
	public static final String INTENT_UNLOCK_DEVICE = "pk.qwerty12.nfclockscreenoffenabler.UNLOCK_DEVICE";

	// The intent above is replaced by the one below if the above is used with adb.
	public static final String INTENT_UNLOCK_INTERCEPTED = "pk.qwerty12.nfclockscreenoffenabler.UNLOCK_ATTEMPT_INTERCEPTED";

	// Converting byte[] to hex string, used to convert NFC UUID to String
	public static String byteArrayToHexString(byte [] inarray) {
		int i, j, in;
		String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
		String out= "";

		for (j = 0 ; j < inarray.length ; ++j) {
			in = (int) inarray[j] & 0xff;
			i = (in >> 4) & 0x0f;
			out += hex[i];
			i = in & 0x0f;
			out += hex[i];
		}
		return out;
	}
}

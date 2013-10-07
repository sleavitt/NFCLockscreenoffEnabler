package pk.qwerty12.nfclockscreenoffenabler;

public class NfcTag {
	private String mNfcTagName = null;
	private String mNfcTagId = null;

	public NfcTag() {

	}

	public NfcTag(String tagId, String tagName) {
		setTagId(tagId);
		setTagName(tagName);
	}

	public String getTagName() {
		return mNfcTagName;
	}

	public void setTagName(String name) {
		mNfcTagName = name;
	}

	public String getTagId() {
		return mNfcTagId;
	}

	public void setTagId(String id) {
		mNfcTagId = id;
	}

	public void setTagId(byte[] uuid) {
		mNfcTagId = Common.byteArrayToHexString(uuid);
	}
}

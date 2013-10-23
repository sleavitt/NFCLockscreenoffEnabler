package pk.qwerty12.nfclockscreenoffenabler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.haarman.listviewanimations.ArrayAdapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class NfcTagArrayAdapter extends ArrayAdapter<NfcTag> {

	private Context mContext = null;
	private ArrayList<NfcTag> mNfcTags = null;

	public NfcTagArrayAdapter(Context context, int resource, ArrayList<NfcTag> nfcTags) {
		super(nfcTags);
		mNfcTags = nfcTags;
		mContext = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.nfc_tag_row, null);
		}
		NfcTag nfcTag = mNfcTags.get(position);
		if (nfcTag != null) {
			TextView tagIdTextview = (TextView) v.findViewById(R.id.nfc_tag_id_textview);
			TextView tagNameTextview = (TextView) v.findViewById(R.id.nfc_tag_friendly_name_textview);

			if (tagIdTextview != null) {
				tagIdTextview.setText(nfcTag.getTagId());
			}
			if (tagNameTextview != null) {
				tagNameTextview.setText(nfcTag.getTagName());
			}

		}
		return v;
	}

	public Set<String> getTagNames() {
		HashSet<String> names = new HashSet<String>();
		for (int i = 0; i < getCount(); i++) {
			names.add(getItem(i).getTagName());
		}

		return names;
	}

	public Set<String> getTagIds() {
		HashSet<String> ids = new HashSet<String>();
		for (int i = 0; i < getCount(); i++) {
			ids.add(getItem(i).getTagId());
		}

		return ids;
	}

	public boolean containsTagId(String nfcTagId) {
		for (int i = 0; i < getCount(); i++) {
			if (nfcTagId.equals(getItem(i).getTagId()))
				return true;
		}

		return false;
	}

	public void remove(int position) {
		NfcTag tag = getItem(position);
		remove(tag);
	}

	/* Fixes out of bounds exception when using com.haarman.listviewanimations.ArrayAdapter
	 * (non-Javadoc)
	 * @see com.haarman.listviewanimations.ArrayAdapter#add(java.lang.Object)
	 * 
	 * Apparently the add method doesn't update the linked ArrayList.
	 */
	@Override
	public void add(NfcTag item) {
		mNfcTags.add(item);
		super.add(item);
	}

	/* Just to be sure */
	@Override
	public void remove(NfcTag item) {
		mNfcTags.remove(item);
		super.remove(item);
	}
}

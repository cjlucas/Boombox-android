package net.cjlucas.boomboxdemo;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.*;

import net.cjlucas.boombox.Boombox;
import net.cjlucas.boombox.provider.AudioDataProvider;

import java.util.List;
import java.util.Locale;

public class ProviderListFragment extends ListFragment
{
    private static final String TAG = "ProviderListFragment";

    private Boombox mBoombox;

	public interface OnProviderSelectedListener
	{
		void onProviderSelected(int index);
	}

    private class PlaylistAdapter extends ArrayAdapter<AudioDataProvider> {
        public PlaylistAdapter(Context context, int resourceId, List<AudioDataProvider> playlist) {
            super(context, resourceId, playlist);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                  view = getActivity().getLayoutInflater().inflate(
                          android.R.layout.simple_list_item_1, parent, false);
            }

            String s = String.format(Locale.getDefault(), "%d. %s", position + 1, getItem(position).getId());

            ((TextView)view.findViewById(android.R.id.text1)).setText(s);

            return view;
        }
    }

	private ArrayAdapter<AudioDataProvider> adapter;

//	@Override
//	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance)
//	{
//		return inflater.inflate(R.layout.provider_list_fragment, container, false);
//	}

    public MainActivity getMainActivity() {
        Activity activity = getActivity();
        return activity == null ? null : (MainActivity)activity;
    }

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);


		Log.d(TAG, "I'm being attached!");
	}

    public void setBoombox(Boombox boombox) {
        mBoombox = boombox;

        this.adapter = new PlaylistAdapter(getActivity(), android.R.layout.simple_list_item_1, boombox.getPlaylist());
        setListAdapter(this.adapter);

        Log.d(TAG, "omghere: " + this.adapter.getCount());
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Log.d(TAG, position + " clicked");
        mBoombox.play(position);
        if (getActivity() instanceof ProviderListActivity) getActivity().finish();
    }

}

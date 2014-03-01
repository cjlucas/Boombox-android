package net.cjlucas.boomboxdemo;

import android.app.Activity;
import android.app.ListFragment;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.*;

import net.cjlucas.boombox.provider.AudioDataProvider;

public class ProviderListFragment extends ListFragment
{
	public interface OnProviderSelectedListener
	{
		void onProviderSelected(int index);
	}

	private ArrayAdapter<AudioDataProvider> adapter;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance)
	{
		return inflater.inflate(R.layout.provider_list_fragment, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		this.adapter = new ArrayAdapter<AudioDataProvider>(getActivity(), R.id.text1);
		this.adapter.addAll( ( (MainActivity)getActivity() ).getProviders() );
		setListAdapter(this.adapter);

		System.out.println( "omghere: " + this.adapter.getCount() );

		System.out.println("I'm being attached!");
	}

	/*
	     *@Override
	     *public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	     *{
	     *    System.out.println("onCreateView");
	     *    return inflater.inflate(R.layout.provider_list_fragment, container, false);
	     *}
	 */
}

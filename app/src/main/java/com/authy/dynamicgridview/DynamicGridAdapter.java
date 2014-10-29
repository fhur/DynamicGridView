package com.authy.dynamicgridview;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by fernandinho on 10/27/14.
 */
public abstract class DynamicGridAdapter<T> extends BaseAdapter {

    private List<T> data;

    public DynamicGridAdapter(){
        super();
        data = new ArrayList<T>();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public T getItem(int position) {
        return data.get(position);
    }

    public void add(T item){
        data.add(item);
    }

    public void addAll(List<T> items){
        data.addAll(items);
        notifyDataSetChanged();
    }

    public void addAll(T... items){
        Collections.addAll(data, items);
        notifyDataSetChanged();
    }

    public void swap(int pos1, int pos2){
        T first = data.get(pos1);
        T second = data.get(pos2);
        data.set(pos1, second);
        data.set(pos2, first);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return -1;
    }
}

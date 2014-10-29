package com.authy.dynamicgridview;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.TextView;


public class TestActivity extends Activity {

    private GridView gridView;
    private TestAdapter adapter;
    private DynamicGridController dynamicGridController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        adapter = new TestAdapter();

        for (int i = 0 ; i < 200 ; i++){
            adapter.add(i+""+i);
        }

        gridView = (GridView)findViewById(R.id.grid);
        gridView.setAdapter(adapter);
        gridView.setNumColumns(4);

        dynamicGridController = new DynamicGridController(gridView, adapter);
    }


    public class TestAdapter extends DynamicGridAdapter<String>{

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = new TextView(getApplicationContext());
            textView.setGravity(Gravity.CENTER);
            textView.setText("\n\n"+getItem(position)+"\n\n");

            int r = Integer.parseInt(getItem(position));
            textView.setBackgroundColor(Color.rgb((r*10)%255, (r*10)%255, (r*10)%255));
            return textView;
        }
    }

}

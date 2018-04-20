package com.google.ar.core.examples.java.helloar;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.view.ViewGroup.LayoutParams;

import java.util.ArrayList;

public class displayTable extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_table);
    }

    public void switchToARActivity(View view) {
        setContentView(R.layout.activity_helloar);
        HelloArActivity.INSTANCE.recreate();
    }

    public void addRowToTable(ArrayList<ARProperty> listObjects){

        TableLayout t1;
        TableLayout tl = (TableLayout) findViewById(R.id.main_table);

        TableRow tr_head = new TableRow(this);

        addColumn(tr_head, "ITEM");
        addColumn(tr_head, "VALUE");
        //addColumn(tr_head, "IMAGE FILE LOCATION");

        tl.addView(tr_head, new TableLayout.LayoutParams(
                LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));

        String propDesc;
        String cost;
        //String fileLocation;

        TableRow tr = new TableRow(this);

        for (int i = 0; i < listObjects.size(); i++){
            propDesc = listObjects.get(i).propertyDescription;
            cost = listObjects.get(i).cost.toString();
            //fileLocation = listObjects.get(i).fileLocation;

            addColumn(tr_head, propDesc);
            addColumn(tr_head, cost);
            //addColumn(tr_head, fileLocation);

            tl.addView(tr, new TableLayout.LayoutParams(
                    LayoutParams.FILL_PARENT,
                    LayoutParams.WRAP_CONTENT));

        }
    }

    public void addColumn(TableRow tr, String displayText){
        TextView label_text = new TextView(this);
        label_text.setText(displayText);
        label_text.setTextColor(Color.WHITE);
        label_text.setPadding(5, 5, 5, 5);
        tr.addView(label_text);
    }
}

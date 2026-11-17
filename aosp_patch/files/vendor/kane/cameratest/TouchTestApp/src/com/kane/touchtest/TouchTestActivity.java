package com.kane.touchtest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;

/** Full-screen grid of buttons; every click is logged with tag "TouchTest". */
public class TouchTestActivity extends Activity {
    private static final String TAG = "TouchTest";
    private static final int COLS = 4;
    private static final int ROWS = 3;

    private final int[] mClicks = new int[COLS * ROWS];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setRowCount(ROWS);
        grid.setBackgroundColor(0xFF263238);
        for (int i = 0; i < COLS * ROWS; i++) {
            final int index = i;
            final Button b = new Button(this);
            b.setText("Nút " + (i + 1));
            b.setTextSize(24);
            b.setOnClickListener(v -> {
                mClicks[index]++;
                b.setText("Nút " + (index + 1) + "\nclick " + mClicks[index]);
                Log.i(TAG, "CLICK button " + (index + 1) + " count=" + mClicks[index]);
            });
            final GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLS, 1f), GridLayout.spec(i % COLS, 1f));
            lp.width = 0;
            lp.height = 0;
            lp.setGravity(Gravity.FILL);
            lp.setMargins(8, 8, 8, 8);
            grid.addView(b, lp);
        }
        setContentView(grid);
    }
}

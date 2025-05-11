package com.example.workhourcalculator;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private LinearLayout subjectsLayout;
    private TextView totalHoursTextView, needTextView;
    private SharedPreferences prefs;
    private static int currentDay = 1;
    private final double targetHours = 400;
    private final int totalDays = 50;
    private static String timeFormatted = "";
    private static String firstPart = "";
    private static String lastPart = "";
    private static int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("gwaprefs", MODE_PRIVATE);

        subjectsLayout = findViewById(R.id.subjectsLayout);
        totalHoursTextView = findViewById(R.id.totalHoursTextView);
        needTextView        = findViewById(R.id.needTextView);
        Button addRow    = findViewById(R.id.addRow);
        Button removeRow = findViewById(R.id.removeRow);
        TextView dateLabel= findViewById(R.id.dateLabel);
        TextView dayLabel = findViewById(R.id.dayLabel);

        // restore row count
        int savedCount = prefs.getInt("rowCount", 1);
//        currentDay = 1;
        dateLabel.setText("Date");
        setDayLabel(savedCount);

        addRow.setOnClickListener(v -> { addNewRow(); saveRowCount(); });
        removeRow.setOnClickListener(v -> { removeLastRow(); saveRowCount(); });

        // build the saved rows
        for(int i=0;i<savedCount;i++){
            addNewRow();
        }
        calculateAllHours();
    }

    private void addNewRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.new_row, subjectsLayout, false);
        EditText dayInput     = row.findViewById(R.id.day);
        EditText dateInput    = row.findViewById(R.id.dateInput);
        EditText timeInInput  = row.findViewById(R.id.timeIn);
        EditText timeOutInput = row.findViewById(R.id.timeOut);
        EditText hoursInput   = row.findViewById(R.id.hours);

        Calendar calendar = Calendar.getInstance();
        String monthAbbreviation = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
//        dateInput.setText(monthAbbreviation + " - ");

        int childCount = subjectsLayout.getChildCount();

        if (childCount > 0) {
            View lastRow = subjectsLayout.getChildAt(childCount - 1);
            EditText timeIn = lastRow.findViewById(R.id.timeIn);
            EditText timeOut= lastRow.findViewById(R.id.timeOut);
            EditText hrs    = lastRow.findViewById(R.id.hours);

            String inText  = timeIn.getText().toString().trim();
            String outText = timeOut.getText().toString().trim();
            int red  = ContextCompat.getColor(this, R.color.lightRed);
            int gray = ContextCompat.getColor(this, R.color.lightGray);

            if (inText.isEmpty() || outText.isEmpty()) {
                timeIn.setHintTextColor(red);
                timeOut.setHintTextColor(red);
                hrs.setText("?");
                Toast.makeText(this, "Empty input", Toast.LENGTH_SHORT).show();
            } else {
                timeIn.setHintTextColor(gray);
                timeOut.setHintTextColor(gray);
            }
        }

        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> {
            scrollView.fullScroll(View.FOCUS_DOWN);
            timeInInput.requestFocus();
        });

        // autosave
        int rowIndex = subjectsLayout.getChildCount();
        setupAutoSave(dayInput,     "day_"     + rowIndex);
        setupAutoSave(dateInput,    "date_"    + rowIndex);
        setupAutoSave(timeInInput,  "in_"      + rowIndex);
        setupAutoSave(timeOutInput, "out_"     + rowIndex);
        setupAutoSave(hoursInput,   "hours_"   + rowIndex);

        EditText dateNewInput = row.findViewById(R.id.dateInput);
        int tmpYearToday = Calendar.getInstance().get(Calendar.YEAR);
        String yearToday = String.format("%02d", tmpYearToday % 100);

        if (dateNewInput != null && dateNewInput.getText().toString().isEmpty()) {
            dateNewInput.setText(yearToday + ", " + monthAbbreviation + " - " + dayOfMonth);
        } //else Toast.makeText(this, "Date is null", Toast.LENGTH_SHORT).show();

        // recalc on focus loss
        View.OnFocusChangeListener fcl = (v, hasFocus) -> { if (!hasFocus) calculateAllHours(); };
        timeInInput.setOnFocusChangeListener(fcl);
        timeOutInput.setOnFocusChangeListener(fcl);

        count = subjectsLayout.getChildCount();
        subjectsLayout.addView(row);

        EditText days = row.findViewById(R.id.day);
        if (days != null) {
            days.setText(String.valueOf(count + 1));
        } else Toast.makeText(this, "Days is null", Toast.LENGTH_SHORT).show();
        setDayLabel(count);
    }

    private void removeLastRow() {
        count = subjectsLayout.getChildCount();
        if (count > 0) {
            int last = count - 1;
            // remove prefs for that row
            prefs.edit()
                    .remove("day_"   + last)
                    .remove("date_"  + last)
                    .remove("in_"    + last)
                    .remove("out_"   + last)
                    .remove("hours_" + last)
                    .apply();
            subjectsLayout.removeViewAt(last);
            currentDay--;
        }
        setDayLabel(count - 2);
    }

    private void calculateAllHours() {
        double totalHours = 0;
        int daysCounted = 0;
        for (int i = 0; i < subjectsLayout.getChildCount(); i++) {
            View row = subjectsLayout.getChildAt(i);
            EditText inV  = row.findViewById(R.id.timeIn);
            EditText outV = row.findViewById(R.id.timeOut);
            EditText hrsV = row.findViewById(R.id.hours);
            String inText  = inV.getText().toString().trim();
            String outText = outV.getText().toString().trim();
            int red = ContextCompat.getColor(this, R.color.lightRed);
            int gray = ContextCompat.getColor(this, R.color.darkWhite);
            if (!inText.isEmpty() && !outText.isEmpty()) {
                row.<EditText>findViewById(R.id.hours).setTextColor(gray);
                try {
                    double timeIn  = Double.parseDouble(inText);
                    double timeOut = Double.parseDouble(outText);
                    if (timeIn<=0||timeOut<=0||timeIn>24||timeOut>24) {
                        if (timeIn<=0||timeIn>24)   inV.setTextColor(red);
                        if (timeOut<=0||timeOut>24) outV.setTextColor(red);
                        Toast.makeText(this, "Time out of bound", Toast.LENGTH_SHORT).show();
                        throw new NumberFormatException();
                    } else {
                        int blue = ContextCompat.getColor(this, R.color.blueWhite);
                        inV.setTextColor(blue);
                        outV.setTextColor(blue);
                    }
                    int lunch = 0;
                    if (timeOut < timeIn) {
                        lunch = 1;
                        timeOut += 12;
                    }
                    double worked = (timeOut - timeIn) - lunch;
                    if (timeIn == timeOut){
                        if (timeIn > 12) worked = timeIn;
                        else worked = 12;
                    }
                    if (worked<0) worked=0;
                    hrsV.setText(String.format(Locale.getDefault(),"%.1f", worked));
                    totalHours += worked;
                    daysCounted++;
                } catch (NumberFormatException e) {
                    row.<EditText>findViewById(R.id.hours).setText("?");
                    row.<EditText>findViewById(R.id.hours).setTextColor(red);
                }
            } else {
                row.<EditText>findViewById(R.id.hours).setText("?");
            }
        }

        double hoursNeeded  = targetHours - totalHours;
        int remainingDays   = totalDays - daysCounted;
        if (hoursNeeded<0) hoursNeeded=0;
        if (remainingDays<0) remainingDays=0;
        String tmpH = hoursNeeded<2?"":"s";
        String tmpD = remainingDays<2?"":"s";

        double needMin    = (totalHours % 1)*60;
        String needMinStr = needMin<2?"":(":"+((int)needMin));
        double haveMin    = (totalHours % 1)*60;
        String haveMinStr = haveMin<2?"":(":"+((int)haveMin));

        double daysConv = totalHours/24;
        String tmpDays  = daysConv<2?"":"s";

        timeFormatted = String.format(Locale.getDefault(),
                "%02d:%02d%s",
                (int)(totalHours/24),
                (int)(totalHours%24),
                haveMinStr
        );
        SpannableStringBuilder sb = new SpannableStringBuilder();
        firstPart = String.format(Locale.getDefault(),"Time Taken:\n %.1f hr%s | ", totalHours, tmpH);
        lastPart  = String.format(Locale.getDefault(),"\n %.1f day%s", daysConv, tmpDays);
        sb.append(firstPart);
        SpannableString struck = new SpannableString(timeFormatted);
        struck.setSpan(new StrikethroughSpan(),0,timeFormatted.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(struck).append(lastPart);
        totalHoursTextView.setText(sb);

        timeFormatted = String.format(Locale.getDefault(),
                "%02d:%02d%s",
                (int)(hoursNeeded/24),
                (int)(hoursNeeded%24),
                needMin<1?":00":needMinStr
        );
        sb = new SpannableStringBuilder();
        firstPart = String.format(Locale.getDefault(),"Need:\n %.1f hr%s | ", hoursNeeded, tmpH);
        sb.append(firstPart);
        struck = new SpannableString(timeFormatted);
        struck.setSpan(new StrikethroughSpan(),0,timeFormatted.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        lastPart = String.format(Locale.getDefault(),"\n within %d day%s", remainingDays, tmpD);
        sb.append(struck).append(lastPart);
        needTextView.setText(sb);
    }

    private void setupAutoSave(EditText et, String key) {
        et.setText(prefs.getString(key,""));
        et.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){
                prefs.edit().putString(key,s.toString()).apply();
            }
            @Override public void afterTextChanged(Editable s){}
        });
    }

    private void saveRowCount() {
        prefs.edit().putInt("rowCount", subjectsLayout.getChildCount()).apply();
    }

    private void setDayLabel(int c){
        TextView dayLabel = findViewById(R.id.dayLabel);
        dayLabel.setText(c>0?"Days":"Day");
    }
}

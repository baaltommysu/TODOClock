package com.todoclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "todo_clock";
    private static final String TASKS = "tasks";
    private static final String TASK_DATE = "task_date";
    private static final String HANGZHOU_WEATHER = "weather_hangzhou";
    private static final String NANJING_WEATHER = "weather_nanjing";
    private static final long WEATHER_REFRESH_INTERVAL_MS = 10 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Task> taskList = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE  ·  M月d日", Locale.CHINA);

    private FlipClockView flipClock;
    private TextView dateLabel;
    private TextView hangzhouTemp;
    private TextView hangzhouDetails;
    private TextView nanjingTemp;
    private TextView nanjingDetails;
    private TextView taskSummary;
    private LinearLayout taskContainer;
    private SharedPreferences preferences;
    private int lastOrientation;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            if (flipClock != null) {
                flipClock.setTime(now);
            }
            if (dateLabel != null) {
                dateLabel.setText(dateFormat.format(now));
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable weatherTicker = new Runnable() {
        @Override
        public void run() {
            loadWeather();
            handler.postDelayed(this, WEATHER_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        loadTasks();
        lastOrientation = getResources().getConfiguration().orientation;
        buildDashboard();
        loadWeather();
        handler.postDelayed(weatherTicker, WEATHER_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        handler.removeCallbacks(weatherTicker);
        handler.postDelayed(weatherTicker, WEATHER_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(weatherTicker);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            handler.postDelayed(this::hideSystemUi, 120L);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // configChanges intercepts rotation, so rebuild the dashboard for the new orientation.
        if (newConfig.orientation != lastOrientation) {
            lastOrientation = newConfig.orientation;
            buildDashboard();
            loadWeather();
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void buildDashboard() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(16));
        root.setBackgroundColor(Color.rgb(16, 19, 22));

        LinearLayout weatherCard = roundedPanel(Color.rgb(32, 40, 45), 18);
        weatherCard.setPadding(dp(24), dp(10), dp(24), dp(10));
        weatherCard.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.setContentDescription("杭州和南京天气，每10分钟自动更新");
        root.addView(weatherCard, new LinearLayout.LayoutParams(-1, dp(116)));

        LinearLayout hangzhouColumn = new LinearLayout(this);
        hangzhouColumn.setOrientation(LinearLayout.VERTICAL);
        hangzhouColumn.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.addView(hangzhouColumn, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView hangzhouLabel = text("杭州 · 本地", 14, Color.rgb(183, 228, 199));
        hangzhouLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hangzhouColumn.addView(hangzhouLabel, new LinearLayout.LayoutParams(-1, dp(24)));
        hangzhouTemp = text("--°", 31, Color.WHITE);
        hangzhouTemp.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hangzhouColumn.addView(hangzhouTemp, new LinearLayout.LayoutParams(-1, dp(40)));
        hangzhouDetails = text("更新中…", 11, Color.rgb(142, 154, 163));
        hangzhouColumn.addView(hangzhouDetails, new LinearLayout.LayoutParams(-1, dp(22)));

        View weatherDivider = new View(this);
        weatherDivider.setBackgroundColor(Color.rgb(75, 88, 94));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(72));
        dividerParams.setMargins(dp(22), 0, dp(22), 0);
        weatherCard.addView(weatherDivider, dividerParams);

        LinearLayout nanjingColumn = new LinearLayout(this);
        nanjingColumn.setOrientation(LinearLayout.VERTICAL);
        nanjingColumn.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.addView(nanjingColumn, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView nanjingLabel = text("南京", 14, Color.rgb(183, 228, 199));
        nanjingLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nanjingColumn.addView(nanjingLabel, new LinearLayout.LayoutParams(-1, dp(24)));
        nanjingTemp = text("--°", 31, Color.WHITE);
        nanjingTemp.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nanjingColumn.addView(nanjingTemp, new LinearLayout.LayoutParams(-1, dp(40)));
        nanjingDetails = text("更新中…", 11, Color.rgb(142, 154, 163));
        nanjingColumn.addView(nanjingDetails, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        contentParams.setMargins(0, dp(16), 0, 0);
        root.addView(content, contentParams);

        LinearLayout clockPanel = roundedPanel(Color.rgb(26, 32, 36), 22);
        clockPanel.setOrientation(LinearLayout.VERTICAL);
        clockPanel.setPadding(dp(22), dp(20), dp(22), dp(16));
        content.addView(clockPanel, portrait
                ? new LinearLayout.LayoutParams(-1, 0, 1f)
                : new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout clockHeading = new LinearLayout(this);
        clockHeading.setGravity(Gravity.CENTER_VERTICAL);
        clockPanel.addView(clockHeading, new LinearLayout.LayoutParams(-1, dp(32)));

        dateLabel = text(dateFormat.format(new Date()), 17, Color.rgb(244, 247, 245));
        dateLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        clockHeading.addView(dateLabel, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView clockTag = text("FLIP CLOCK", 10, Color.rgb(142, 154, 163));
        clockTag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        clockTag.setLetterSpacing(0.12f);
        clockHeading.addView(clockTag, new LinearLayout.LayoutParams(-2, -1));

        flipClock = new FlipClockView(this);
        flipClock.setTime(new Date());
        clockPanel.addView(flipClock, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout clockFooter = new LinearLayout(this);
        clockFooter.setGravity(Gravity.CENTER_VERTICAL);
        clockPanel.addView(clockFooter, new LinearLayout.LayoutParams(-1, dp(26)));
        TextView clockHint = text("每分钟自动翻页  ·  保持屏幕常亮", 11, Color.rgb(142, 154, 163));
        clockFooter.addView(clockHint, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView clockDot = text("●", 11, Color.rgb(183, 228, 199));
        clockFooter.addView(clockDot, new LinearLayout.LayoutParams(-2, -1));

        LinearLayout todoPanel = roundedPanel(Color.rgb(26, 32, 36), 22);
        todoPanel.setOrientation(LinearLayout.VERTICAL);
        todoPanel.setPadding(dp(18), dp(12), dp(18), dp(10));
        LinearLayout.LayoutParams todoParams = portrait
                ? new LinearLayout.LayoutParams(-1, 0, 1f)
                : new LinearLayout.LayoutParams(dp(300), -1);
        todoParams.setMargins(portrait ? 0 : dp(12), portrait ? dp(12) : 0, 0, 0);
        content.addView(todoPanel, todoParams);

        LinearLayout todoHeader = new LinearLayout(this);
        todoHeader.setGravity(Gravity.CENTER_VERTICAL);
        todoPanel.addView(todoHeader, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout todoTitleGroup = new LinearLayout(this);
        todoTitleGroup.setOrientation(LinearLayout.VERTICAL);
        todoTitleGroup.setGravity(Gravity.CENTER_VERTICAL);
        todoHeader.addView(todoTitleGroup, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView todoTitle = text("今日事项", 20, Color.rgb(244, 247, 245));
        todoTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        todoTitleGroup.addView(todoTitle, new LinearLayout.LayoutParams(-1, dp(28)));
        taskSummary = text("准备开始", 11, Color.rgb(142, 154, 163));
        todoTitleGroup.addView(taskSummary, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView addButton = text("＋", 28, Color.rgb(16, 19, 22));
        addButton.setGravity(Gravity.CENTER);
        addButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addButton.setBackground(roundedDrawable(Color.rgb(183, 228, 199), 14));
        addButton.setContentDescription("添加今日事项");
        addButton.setOnClickListener(v -> showAddTaskDialog());
        todoHeader.addView(addButton, new LinearLayout.LayoutParams(dp(46), dp(40)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        todoPanel.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        taskContainer = new LinearLayout(this);
        taskContainer.setOrientation(LinearLayout.VERTICAL);
        taskContainer.setPadding(0, dp(8), 0, dp(6));
        scrollView.addView(taskContainer, new ScrollView.LayoutParams(-1, -2));

        TextView clearButton = text("清除已完成", 12, Color.rgb(142, 154, 163));
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setBackground(roundedDrawable(Color.rgb(32, 40, 45), 12));
        clearButton.setOnClickListener(v -> clearCompleted());
        todoPanel.addView(clearButton, new LinearLayout.LayoutParams(-1, dp(38)));

        setContentView(root);
        renderTasks();
    }

    private void loadWeather() {
        if (hangzhouTemp == null || hangzhouDetails == null || nanjingTemp == null || nanjingDetails == null) {
            return;
        }
        WeatherData cachedHangzhou = readWeatherCache(HANGZHOU_WEATHER);
        WeatherData cachedNanjing = readWeatherCache(NANJING_WEATHER);
        updateWeatherViews(hangzhouTemp, hangzhouDetails, cachedHangzhou);
        updateWeatherViews(nanjingTemp, nanjingDetails, cachedNanjing);
        new Thread(() -> {
            final WeatherData[] results = new WeatherData[2];
            Thread hangzhouRequest = new Thread(() -> results[0] = fetchWeather(30.2741, 120.1551));
            Thread nanjingRequest = new Thread(() -> results[1] = fetchWeather(32.0603, 118.7969));
            hangzhouRequest.start();
            nanjingRequest.start();
            try {
                hangzhouRequest.join();
                nanjingRequest.join();
                runOnUiThread(() -> {
                    if (results[0] != null) {
                        saveWeatherCache(HANGZHOU_WEATHER, results[0]);
                        updateWeatherViews(hangzhouTemp, hangzhouDetails, results[0]);
                    } else if (cachedHangzhou == null) {
                        updateWeatherViews(hangzhouTemp, hangzhouDetails, null);
                    }
                    if (results[1] != null) {
                        saveWeatherCache(NANJING_WEATHER, results[1]);
                        updateWeatherViews(nanjingTemp, nanjingDetails, results[1]);
                    } else if (cachedNanjing == null) {
                        updateWeatherViews(nanjingTemp, nanjingDetails, null);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (cachedHangzhou == null) {
                        updateWeatherViews(hangzhouTemp, hangzhouDetails, null);
                    }
                    if (cachedNanjing == null) {
                        updateWeatherViews(nanjingTemp, nanjingDetails, null);
                    }
                });
            }
        }).start();
    }

    private void saveWeatherCache(String key, WeatherData data) {
        preferences.edit()
                .putInt(key + "_temperature", data.temperature)
                .putInt(key + "_humidity", data.humidity)
                .putString(key + "_description", data.description)
                .putLong(key + "_updated", System.currentTimeMillis())
                .apply();
    }

    private WeatherData readWeatherCache(String key) {
        if (!preferences.contains(key + "_temperature")) {
            return null;
        }
        return new WeatherData(
                preferences.getInt(key + "_temperature", 0),
                preferences.getInt(key + "_humidity", 0),
                preferences.getString(key + "_description", "天气"));
    }

    private WeatherData fetchWeather(double latitude, double longitude) {
        HttpURLConnection connection = null;
        try {
            String endpoint = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,relative_humidity_2m,weather_code"
                            + "&timezone=Asia%%2FShanghai", latitude, longitude);
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            JSONObject current = new JSONObject(readStream(connection.getInputStream())).optJSONObject("current");
            if (current == null) {
                return null;
            }
            return new WeatherData(
                    (int) Math.round(current.optDouble("temperature_2m", 0)),
                    current.optInt("relative_humidity_2m", 0),
                    weatherDescription(current.optInt("weather_code", -1)));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void updateWeatherViews(TextView temperatureView, TextView detailsView, WeatherData data) {
        if (data == null) {
            temperatureView.setText("--°");
            detailsView.setText("暂不可用");
            return;
        }
        temperatureView.setText(data.temperature + "°");
        detailsView.setText(data.description + " · 湿度" + data.humidity + "%");
    }

    private String readStream(InputStream inputStream) throws Exception {
        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        return result.toString();
    }

    private String weatherDescription(int code) {
        if (code == 0) return "晴朗";
        if (code == 1 || code == 2) return "晴间多云";
        if (code == 3) return "阴天";
        if (code == 45 || code == 48) return "有雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 67) return "下雨";
        if (code >= 71 && code <= 77) return "下雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code == 95 || code == 96 || code == 99) return "雷雨";
        return "杭州天气";
    }

    private void showAddTaskDialog() {
        if (!BuildConfig.IS_PRO && activeTaskCount() >= 3) {
            new AlertDialog.Builder(this)
                    .setTitle("普通版事项已满")
                    .setMessage("普通版最多同时保留 3 条未完成事项。专业版支持无限事项。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.rgb(244, 247, 245));
        input.setHintTextColor(Color.rgb(142, 154, 163));
        input.setHint("例如：完成今天最重要的一件事");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(22), dp(4), dp(22), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加今日事项")
                .setView(wrapper)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();
        dialog.setOnDismissListener(ignored -> handler.postDelayed(this::hideSystemUi, 120L));
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                input.setError("写点什么吧");
                return;
            }
            taskList.add(new Task(value, false));
            saveTasks();
            renderTasks();
            dialog.dismiss();
        }));
        dialog.show();
        input.requestFocus();
    }

    private void clearCompleted() {
        boolean changed = false;
        for (int i = taskList.size() - 1; i >= 0; i--) {
            if (taskList.get(i).done) {
                taskList.remove(i);
                changed = true;
            }
        }
        if (changed) {
            saveTasks();
            renderTasks();
        } else {
            Toast.makeText(this, "还没有已完成的事项", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderTasks() {
        if (taskContainer == null) {
            return;
        }
        taskContainer.removeAllViews();
        int completed = 0;
        for (Task task : taskList) {
            if (task.done) completed++;
        }
        if (taskList.isEmpty()) {
            TextView empty = text("点击右上角 ＋\n添加今天要完成的第一件事", 14, Color.rgb(142, 154, 163));
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(4), 1f);
            taskContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(160)));
        } else {
            for (Task task : taskList) {
                addTaskRow(task);
            }
        }
        if (taskSummary != null) {
            if (taskList.isEmpty()) {
                taskSummary.setText(BuildConfig.IS_PRO ? "专业版 · 无限事项" : "普通版 · 最多3项");
            } else {
                String edition = BuildConfig.IS_PRO ? "专业版 · " : "普通版 · ";
                taskSummary.setText(edition + completed + " / " + taskList.size() + " 已完成");
            }
        }
    }

    private int activeTaskCount() {
        int active = 0;
        for (Task task : taskList) {
            if (!task.done) {
                active++;
            }
        }
        return active;
    }

    private void addTaskRow(Task task) {
        LinearLayout row = roundedPanel(Color.rgb(32, 40, 45), 15);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(12), 0);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(56));
        rowParams.setMargins(0, 0, 0, dp(8));
        taskContainer.addView(row, rowParams);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(task.done);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{}
            };
            int[] colors = new int[]{Color.rgb(183, 228, 199), Color.rgb(142, 154, 163)};
            checkBox.setButtonTintList(new ColorStateList(states, colors));
        }
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(40), -1));

        TextView taskTitle = text(task.title, 15, task.done ? Color.rgb(110, 122, 130) : Color.rgb(244, 247, 245));
        taskTitle.setGravity(Gravity.CENTER_VERTICAL);
        taskTitle.setMaxLines(2);
        taskTitle.setEllipsize(TextUtils.TruncateAt.END);
        if (task.done) {
            taskTitle.setPaintFlags(taskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        row.addView(taskTitle, new LinearLayout.LayoutParams(0, -1, 1f));

        checkBox.setOnCheckedChangeListener((button, checked) -> {
            task.done = checked;
            saveTasks();
            renderTasks();
        });
        row.setOnClickListener(v -> checkBox.toggle());
        row.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("删除事项？")
                    .setMessage(task.title)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        taskList.remove(task);
                        saveTasks();
                        renderTasks();
                    })
                    .show();
            return true;
        });
    }

    private void loadTasks() {
        taskList.clear();
        String raw = preferences.getString(TASKS, "");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String savedDate = preferences.getString(TASK_DATE, "");
        if (TextUtils.isEmpty(raw)) {
            preferences.edit().putString(TASK_DATE, today).apply();
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String title = item.optString("title", "").trim();
                if (!TextUtils.isEmpty(title)) {
                    boolean done = item.optBoolean("done", false);
                    if (today.equals(savedDate) || !done) {
                        taskList.add(new Task(title, done));
                    }
                }
            }
            if (!today.equals(savedDate)) {
                saveTasks();
            }
        } catch (Exception ignored) {
            preferences.edit().remove(TASKS).apply();
        }
    }

    private void saveTasks() {
        JSONArray array = new JSONArray();
        for (Task task : taskList) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", task.title);
                item.put("done", task.done);
                array.put(item);
            } catch (Exception ignored) {
                // A single malformed item should not prevent the remaining list from saving.
            }
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        preferences.edit()
                .putString(TASKS, array.toString())
                .putString(TASK_DATE, today)
                .apply();
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout roundedPanel(int color, float radiusDp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setBackground(roundedDrawable(color, radiusDp));
        return panel;
    }

    private GradientDrawable roundedDrawable(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Task {
        final String title;
        boolean done;

        Task(String title, boolean done) {
            this.title = title;
            this.done = done;
        }
    }

    private static class WeatherData {
        final int temperature;
        final int humidity;
        final String description;

        WeatherData(int temperature, int humidity, String description) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.description = description;
        }
    }

    private class FlipClockView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF card = new RectF();
        private String time = "0000";
        private long lastMinute = -1;
        private float flipProgress = 1f;
        private long flipStarted;

        FlipClockView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setTime(Date date) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            String next = String.format(Locale.US, "%02d%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
            long minute = date.getTime() / 60000L;
            if (!next.equals(time) && lastMinute != -1) {
                flipStarted = System.currentTimeMillis();
                flipProgress = 0f;
            }
            time = next;
            lastMinute = minute;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            float gap = dp(10);
            float colonGap = dp(28);
            float digitWidth = (width - colonGap - gap * 3) / 4f;
            float digitHeight = Math.min(dp(170), height - dp(26));
            float left = (width - (digitWidth * 4 + gap * 3 + colonGap)) / 2f;
            float top = Math.max(dp(10), (height - digitHeight) / 2f);
            float radius = dp(13);

            for (int i = 0; i < 4; i++) {
                float x = left + i * (digitWidth + gap);
                if (i >= 2) x += colonGap;
                drawDigit(canvas, x, top, digitWidth, digitHeight, time.charAt(i), radius);
            }

            float colonX = left + digitWidth * 2 + gap * 1.5f;
            paint.setColor(Color.rgb(183, 228, 199));
            canvas.drawCircle(colonX + colonGap / 2f, top + digitHeight * 0.38f, dp(5), paint);
            canvas.drawCircle(colonX + colonGap / 2f, top + digitHeight * 0.62f, dp(5), paint);

            if (flipProgress < 1f) {
                float elapsed = System.currentTimeMillis() - flipStarted;
                flipProgress = Math.min(1f, elapsed / 420f);
                postInvalidateDelayed(16);
            }
        }

        private void drawDigit(Canvas canvas, float x, float y, float width, float height, char digit, float radius) {
            card.set(x, y, x + width, y + height);
            paint.setColor(Color.rgb(23, 28, 31));
            paint.setShadowLayer(dp(10), 0, dp(5), 0x55000000);
            canvas.drawRoundRect(card, radius, radius, paint);
            paint.clearShadowLayer();

            paint.setColor(Color.rgb(36, 43, 47));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height / 2f + dp(2)), radius, radius, paint);
            paint.setColor(Color.rgb(28, 34, 37));
            canvas.drawRect(x, y + height / 2f, x + width, y + height - radius, paint);
            paint.setColor(Color.rgb(13, 16, 18));
            canvas.drawRect(x, y + height / 2f - dp(1), x + width, y + height / 2f + dp(1), paint);

            paint.setColor(Color.rgb(244, 247, 245));
            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paint.setTextSize(dp(78));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float center = y + height / 2f;
            float textOffset = (metrics.ascent + metrics.descent) / 2f;
            float baseline = center - textOffset;
            // Draw one glyph split by the hinge line. Drawing a full glyph in each
            // half makes the clock look like two stacked copies of the time.
            canvas.save();
            canvas.clipRect(x, y, x + width, center);
            canvas.drawText(String.valueOf(digit), x + width / 2f, baseline, paint);
            canvas.restore();
            paint.setColor(Color.rgb(226, 233, 229));
            canvas.save();
            canvas.clipRect(x, center, x + width, y + height);
            canvas.drawText(String.valueOf(digit), x + width / 2f, baseline, paint);
            canvas.restore();
            paint.setColor(Color.rgb(13, 16, 18));
            canvas.drawRect(x, center - dp(1), x + width, center + dp(1), paint);
        }
    }
}

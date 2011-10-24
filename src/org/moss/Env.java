package org.moss;

import org.moss.Parser.Layout;
import org.moss.objects.MossObject;
import org.moss.objects.DataProvider;
import org.moss.objects.Interval;
import org.moss.objects.UpdateManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.FileObserver;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

public class Env {

    /**
     * Singleton holds the current Env
     */
    public enum Current {
        INSTANCE;

        public Env env;
    }

    class ConfigWatcher extends FileObserver {

        private String mFilename;
        private Context mContext;

        public ConfigWatcher(Context context, String directory, String filename) {
            super(directory, FileObserver.MODIFY);
            this.mFilename = filename;
            this.mContext = context;
        }

        public void onEvent(int event, String path) {
            if ((event & FileObserver.MODIFY) != 0 && path.equals(mFilename)) {
                mHandler.post(new Runnable() {
                    public void run() {
                        Env.reload(mContext);
                    }
                });
            }
        }
    }


    public Env() {
        paint = new Paint();
        paint.setColor(0xffffffff);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(1);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setTypeface(Typeface.MONOSPACE);

        exs = new ArrayList<MossException>();
    }

    public void destroy() {
        stopFileWatcher();
    }

    public static void load(Context context, Handler handler) {
        mHandler = handler;
        reload(context);
    }

    public static void reload(Context context) {
        SharedPreferences prefs =
            context.getSharedPreferences(MossPaper.SHARED_PREFS_NAME, 0);
        Env oldEnv = Current.INSTANCE.env;
        Env newEnv = null;
        try {
            if (Config.CUSTOM.equals(prefs.getString("sample_config_file", ""))
                    && null != prefs.getString("config_file", null)
                    && new File(prefs.getString("config_file", "")).exists()) {

                try {
                    Parser p = new Parser();
                    String cfile = prefs.getString("config_file", "");
                    InputStream in = new FileInputStream(cfile);
                    newEnv = new Env();
                    newEnv.configFile = new File(cfile);
                    p.buildEnv(newEnv, in);
                } catch (IOException e) {
                    newEnv = loadDefaultConfig(context, prefs);
                }
            } else {
                newEnv = loadDefaultConfig(context, prefs);
            }

            if (null != oldEnv) {
                oldEnv.stopFileWatcher();
            }
            newEnv.startFileWatcher(context);

            newEnv.loadPrefs(context, prefs);
            newEnv.buildDataProviders();

            synchronized (Current.INSTANCE) {
                if (oldEnv != null) {
                    newEnv.paperHeight = oldEnv.getPaperHeight();
                    newEnv.paperWidth = oldEnv.getPaperWidth();
                    oldEnv.destroy();
                }
                oldEnv = null;
                Current.INSTANCE.env = newEnv;
            }
        } catch (IOException e) {
            /* This is not good */
            Log.e(TAG, "Fatal Error: Could not load any config.", e);
        }
    }

    static Env loadDefaultConfig(Context context, SharedPreferences prefs) throws IOException {
        Parser p = new Parser();
        AssetManager am = context.getAssets();
        String def = prefs.getString("sample_config_file", "default.conf");
        if (Config.CUSTOM.equals(def)) {
            def = "error.conf";
        }
        Env env = new Env();
        p.buildEnv(env, am.open(def));
        return env;
    }

    public void loadPrefs(Context context, SharedPreferences prefs) {

        /* If font size changes, so will the max(X|Y) */
        this.maxX = 0;
        this.maxY = 0;

        if (null != config) {
            this.updateInterval = (long) (1000.0f * config.getUpdateInterval());

            try {
                fontSize = prefs.getFloat("font_size", config.getFontSize());
            } catch (NumberFormatException e) {
                Log.e(TAG, "", e);
                fontSize = Config.CONF_FONT_SIZE_VALUE;
            }
            paint.setTextSize(fontSize);

            backgroundColor = prefs.getInt("background_color", config.getBackgroundColor());
            backgroundColor |= 0xff000000;

            modColor = prefs.getInt("mod_color", config.getModColor());
            if (modColor != -1) {
                modColor |= 0xff000000;
            }
        }

        if (null != config.getBackgroundImagePath()) {
            try {
                backgroundImage =
                    BitmapFactory.decodeFile(config.getBackgroundImagePath());
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }
        lineHeight = paint.getTextSize();
    }

    public void buildDataProviders() {
        long interval = updateInterval;

        dataProviders = new HashMap<DataProvider, UpdateManager>();
        for (Object o : this.layout) {
            if (o instanceof Interval) {
                Interval it = (Interval) o;
                interval = it.getInterval();
            } else if (o instanceof MossObject) {
                MossObject obj = (MossObject) o;
                DataProvider d = obj.getDataProvider();
                if (null != d) {
                    UpdateManager m = dataProviders.get(d);
                    if (m == null) {
                        m = new UpdateManager(d);
                    }
                    m.setInterval(interval);
                    dataProviders.put(d, m);
                }
            }
        }
    }

    public void draw(Canvas c) {
        this.canvas = c;

        c.save();
        c.drawColor(this.backgroundColor);
        if (null != backgroundImage) {
            c.drawBitmap(backgroundImage, 0, 0, paint);
        } else {
            if (this.modColor != -1) {
                int orig = paint.getColor();
                paint.setColor(this.modColor);
                for (int i = 0; i < getPaperWidth() || i < getPaperHeight(); i += 5) {
                    c.drawLine(0, i, getPaperWidth(), i, paint);
                    c.drawLine(i, 0, i, getPaperHeight(), paint);
                }
                paint.setColor(orig);
            }
        }
        float startx = 0.0f, starty = 0.0f;
        if (getMaxX() > 0 && getMaxY() > 0) {
            float mossWidth = getMaxX();
            float mossHeight = getMaxY();
            switch (config.getHAlign()) {
            case RIGHT:
                startx = (getPaperWidth() - mossWidth) - config.getGapX();
                break;
            case MIDDLE:
                startx = (getPaperWidth() - mossWidth) / 2.0f;
                break;
            default:
                startx = config.getGapX();
            }
            switch (config.getVAlign()) {
            case BOTTOM:
                starty = getPaperHeight() - mossHeight - config.getGapY();
                break;
            case MIDDLE:
                starty = (getPaperHeight() - mossHeight) / 2.0f;
                break;
            default:
                starty = config.getGapY();
            }
        }
        c.translate(startx, starty);

        setX(0.0F);
        setY(0.0F);
        Layout l = getLayout();
        if (null != l) {
            for (MossObject o : l) {
                o.preDraw(this);
            }
            for (MossObject o : l) {
                o.draw(this);
            }
            for (MossObject o : l) {
                o.postDraw(this);
            }
        }
        c.restore();
    }

    public void startFileWatcher(Context context) {
        if (configFile == null) {
            return;
        }
        stopFileWatcher();
        if (config.getAutoReload()) {
            cwatcher =
                new ConfigWatcher(context, configFile.getParent(), configFile.getName());
            cwatcher.startWatching();
            Log.i(TAG, "Auto Reload ENABLED");
        } else {
            Log.i(TAG, "Auto Reload DISABLED");
        }
    }

    void stopFileWatcher() {
        if (cwatcher != null) {
            cwatcher.stopWatching();
            cwatcher.mContext = null;
            cwatcher = null;
        }
    }

    public void setX(float x) {
        if (maxX < x) {
            maxX = x;
        }
        this.x = x;
    }

    public float getX() {
        return x;
    }

    public float getMaxX() {
        return maxX;
    }

    public void setY(float y) {
        if (maxY < y) {
            maxY  = y;
        }
        this.y = y;
    }

    public float getY() {
        return y;
    }

    public float getMaxY() {
        return maxY;
    }

    public void setConfig(Config c) {
        this.config = c;
    }

    public Config getConfig() {
        return config;
    }

    public File getConfigFile() {
        return configFile;
    }

    public void setConfigFile(File cf) {
        this.configFile = cf;
    }

    public void setLayout(Layout l) {
        this.layout = l;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setPaint(Paint p) {
        this.paint = p;
    }

    public Paint getPaint() {
        return paint;
    }

    public void setCanvas(Canvas c) {
        this.canvas = c;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Map<DataProvider, UpdateManager> getDataProviders() {
        return dataProviders;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getModColor() {
        return modColor;
    }

    public float getFontSize() {
        return fontSize;
    }

    public void addExs(MossException ex) {
        this.exs.add(ex);
    }

    public boolean hasExs() {
        return this.exs != null && this.exs.size() > 0;
    }

    public List<MossException> getExs() {
        return this.exs;
    }

    public void setLineHeight(float lh) {
        this.lineHeight = Math.max(lh, lineHeight);
    }

    public void resetLineHeight() {
        this.lineHeight = paint.getTextSize();
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public void setPaperHeight(float height) {
        this.paperHeight = height;
    }

    public float getPaperHeight() {
        return paperHeight;
    }

    public void setPaperWidth(float width) {
        this.paperWidth = width;
    }

    public float getPaperWidth() {
        return paperWidth;
    }

    private float paperWidth;
    private float paperHeight;

    /* startx, starty are used for alignment, they translate the canvas to a
     * particular position */
    private float startx;
    private float starty;

    /* holds the current x, y used when drawing */
    private float x;
    private float y;

    /* after an initial draw, these should be populated and can be used to
     * align the canvas */
    private float maxX;
    private float maxY;

    private float lineHeight;
    private Canvas canvas;
    private Paint paint;
    private Config config;
    private Layout layout;
    private Map<DataProvider, UpdateManager> dataProviders;
    private List<MossException> exs;
    private File configFile;

    private long updateInterval;
    private int backgroundColor;
    private int modColor;
    private float fontSize;
    private Bitmap backgroundImage;

    static Handler mHandler;

    private ConfigWatcher cwatcher;

    static final String TAG = "Env";
}

package org.laneby.wallpapers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.FullScreenCarouselStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherChooserActivity extends AppCompatActivity implements View.OnClickListener {

    protected static final float WALLPAPER_SCREENS_SPAN = 2f;
    private static final String PREF_KEY = "wallpaper_prefs";
    protected static final String WALLPAPER_WIDTH_KEY = "wallpaper.width";
    protected static final String WALLPAPER_HEIGHT_KEY = "wallpaper.height";
    private ImageView mImageView;
    private TextView mInfoView;
    private boolean mIsWallpaperSet;
    private Bitmap mBitmap;
    private ArrayList<Integer> mImages;
    private String[] mInfos;
    private ExecutorService mExecutor;
    private Handler mHandler;
    private static SharedPreferences mPrefs;
    private RecyclerView mRecyclerView;

    @SuppressLint("SwitchIntDef")
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mExecutor = Executors.newSingleThreadExecutor();
        mHandler = new Handler(Looper.getMainLooper());

        findWallpapers();

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        setContentView(R.layout.chooser_activity);

        mRecyclerView = findViewById(R.id.gallery);
        CarouselLayoutManager layoutManager = new CarouselLayoutManager(new FullScreenCarouselStrategy());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(new ImageAdapter(this));

        // Add Carousel snapping behavior
        final CarouselSnapHelper snapHelper = new CarouselSnapHelper();
        snapHelper.attachToRecyclerView(mRecyclerView);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapView = snapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        int position = layoutManager.getPosition(snapView);
                        if (position != RecyclerView.NO_POSITION) {
                            loadWallpaper(position);
                        }
                    }
                }
            }
        });

        findViewById(R.id.set).setOnClickListener(this);

        mImageView = findViewById(R.id.wallpaper);
        mInfoView = findViewById(R.id.info);
        mPrefs = getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);

        loadWallpaper(0);
    }


    private void findWallpapers() {
        mImages = new ArrayList<>(24);
        final Resources resources = getResources();
        mInfos = resources.getStringArray(R.array.wallpaper_info);
        addWallpapers(resources, R.array.wallpapers);
    }

    private void addWallpapers(Resources resources, int imagesResId) {
        final TypedArray images = resources.obtainTypedArray(imagesResId);

        for (int i = 0; i < images.length(); i++) {
            int imageRes = images.getResourceId(i, 0);

            if (imageRes != 0) {
                mImages.add(imageRes);
            }
        }
        images.recycle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsWallpaperSet = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
    }

    private void loadWallpaper(int position) {
        mExecutor.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            final Bitmap b = BitmapFactory.decodeResource(getResources(),
                    mImages.get(position), options);

            final String info = mInfos[position];

            mHandler.post(() -> {
                if (b != null) {
                    if (mBitmap != null) {
                        mBitmap.recycle();
                    }
                    mBitmap = b;
                    mImageView.setImageBitmap(mBitmap);
                    mInfoView.setText(info);
                }
            });
        });
    }

    protected boolean isScreenLarge(Resources res) {
        Configuration config = res.getConfiguration();
        return config.smallestScreenWidthDp >= 720;
    }


    protected Point getDefaultWallpaperSize(Resources res, WindowManager windowManager) {
        // Uses suggested size if available
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        int suggestedWidth = wallpaperManager.getDesiredMinimumWidth();
        int suggestedHeight = wallpaperManager.getDesiredMinimumHeight();
        if (suggestedWidth > 0 && suggestedHeight > 0) {
            return new Point(suggestedWidth, suggestedHeight);
        }

        // Else, calculate desired size from screen size
        final int maxDim, minDim;
        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
        Insets insets = windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.statusBars());
        Rect bounds = windowMetrics.getBounds();
        int width = bounds.width() - insets.left - insets.right;
        int height = bounds.height() - insets.top - insets.bottom;
        maxDim = Math.max(width, height);
        minDim = Math.min(width, height);

        // We need to ensure that there is enough extra space in the wallpaper
        // for the intended
        // parallax effects
        final int defaultWidth, defaultHeight;
        if (isScreenLarge(res)) {
            defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
        } else {
            defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
        }
        defaultHeight = maxDim;
        return new Point(defaultWidth, defaultHeight);
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    protected float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    protected RectF getMaxCropRect(
            int inWidth, int inHeight, int outWidth, int outHeight) {
        RectF cropRect = new RectF();
        // Get a crop rect that will fit this
        if (inWidth / (float) inHeight > outWidth / (float) outHeight) {
            cropRect.top = 0;
            cropRect.bottom = inHeight;
            cropRect.left = (inWidth - (outWidth / (float) outHeight) * inHeight) / 2;
            cropRect.right = inWidth - cropRect.left;
        } else {
            cropRect.left = 0;
            cropRect.right = inWidth;
            cropRect.top = (inHeight - (outHeight / (float) outWidth) * inWidth) / 2;
            cropRect.bottom = inHeight - cropRect.top;
        }
        return cropRect;
    }

    protected void cropImageAndSetWallpaper(int resId, int destination) {
        if (mIsWallpaperSet) {
            return;
        }
        mIsWallpaperSet = true; // Prevent multiple sets

        Point outSize = getDefaultWallpaperSize(getResources(), getWindowManager());
        // Pass the destination flag to the BitmapCropTask constructor
        final BitmapCropTask cropTask = new BitmapCropTask(this, getResources(), resId,
                null, 0, outSize.x, outSize.y, true, false, null, destination);
        Point inSize = cropTask.getImageBounds();
        if (inSize == null) {
            mIsWallpaperSet = false; // Reset if failed
            return;
        }
        final RectF crop = getMaxCropRect(inSize.x, inSize.y, outSize.x, outSize.y);
        cropTask.setCropBounds(crop);
        Runnable onEndCrop = () -> {
            Point point = cropTask.getImageBounds();
            if (point != null) {
                updateWallpaperDimensions(point.x, point.y);
            }
            setResult(Activity.RESULT_OK);
            finish();
        };
        cropTask.setOnEndRunnable(onEndCrop);
        cropTask.execute();
    }

    protected void updateWallpaperDimensions(int width, int height) {
        SharedPreferences.Editor editor = mPrefs.edit();
        if (width != 0 && height != 0) {
            editor.putInt(WALLPAPER_WIDTH_KEY, width);
            editor.putInt(WALLPAPER_HEIGHT_KEY, height);
        } else {
            editor.remove(WALLPAPER_WIDTH_KEY);
            editor.remove(WALLPAPER_HEIGHT_KEY);
        }
        editor.apply();

        suggestWallpaperDimension(getResources(), getWindowManager(), WallpaperManager.getInstance(this));
    }

    public void suggestWallpaperDimension(Resources res,
                                          WindowManager windowManager,
                                          final WallpaperManager wallpaperManager) {
        final Point defaultWallpaperSize = getDefaultWallpaperSize(res, windowManager);

        new Thread("suggestWallpaperDimension") {
            public void run() {
                // If we have saved a wallpaper width/height, use that instead
                int savedWidth = mPrefs.getInt(WALLPAPER_WIDTH_KEY, defaultWallpaperSize.x);
                int savedHeight = mPrefs.getInt(WALLPAPER_HEIGHT_KEY, defaultWallpaperSize.y);
                wallpaperManager.suggestDesiredDimensions(savedWidth, savedHeight);
            }
        }.start();
    }

    /*
     * When using touch if you tap an image it triggers both the onItemClick and
     * the onTouchEvent causing the wallpaper to be set twice. Ensure we only
     * set the wallpaper once.
     */
    private void showApplyWallpaperDialog() {
        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (!(layoutManager instanceof CarouselLayoutManager)) return;

        CarouselSnapHelper snapHelper = new CarouselSnapHelper();
        View snapView = snapHelper.findSnapView(layoutManager);
        if (snapView == null) return;

        final int currentPos = layoutManager.getPosition(snapView);
        if (currentPos == RecyclerView.NO_POSITION) return;


        // 1. Get the items and icons
        final String[] items = getResources().getStringArray(R.array.wallpaper_apply_options);
        final int[] icons = new int[]{
                R.drawable.ic_home, // Make sure you have this drawable
                R.drawable.ic_lock, // Make sure you have this drawable
                R.drawable.ic_both  // Make sure you have this drawable
        };

        // 2. Create a custom ArrayAdapter
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.dialog_item_with_icon, // Use our custom layout
                R.id.dialog_item_text, // The ID of the TextView in our layout
                items
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                // Get the default view from the ArrayAdapter
                View view = super.getView(position, convertView, parent);
                // Get our icon and text views
                ImageView iconView = view.findViewById(R.id.dialog_item_icon);
                // Set the icon for the current item
                iconView.setImageResource(icons[position]);
                return view;
            }
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(getResources().getString(R.string.wallpaper_instructions_title))
                .setAdapter(adapter, (dialog, which) -> {
                        int destination;
                    switch (which) {
                        case 0: // Home screen
                            destination = WallpaperManager.FLAG_SYSTEM;
                            break;
                        case 1: // Lock screen
                            destination = WallpaperManager.FLAG_LOCK;
                            break;
                        case 2: // Both
                        default:
                            destination = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                            break;
                    }
                    cropImageAndSetWallpaper(mImages.get(currentPos), destination);
                })
                .show();
    }
    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
        private final LayoutInflater mLayoutInflater;

        ImageAdapter(Context context) {
            mLayoutInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View image = mLayoutInflater.inflate(R.layout.wallpaper_item, parent, false);
            return new ImageViewHolder(image);
        }


        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            int imageRes = mImages.get(position);
            holder.imageView.setImageResource(imageRes);
            Drawable imageDrawable = holder.imageView.getDrawable();
            if (imageDrawable == null) {
                Log.e("Paperless System", String.format(
                        "Error decoding image resId=%d for wallpaper #%d",
                        imageRes, position));
            }
        }

        @Override
        public int getItemCount() {
            return mImages.size();
        }


        class ImageViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView imageView;

            ImageViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.carousel_imageView);
            }
        }
    }

    public void onClick(View v) {
        if (v.getId() == R.id.set) {
            showApplyWallpaperDialog();
        }
    }
}

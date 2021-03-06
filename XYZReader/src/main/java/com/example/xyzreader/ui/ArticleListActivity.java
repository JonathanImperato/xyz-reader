package com.example.xyzreader.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mToolbar = (Toolbar) findViewById(R.id.detail_toolbar);


        final View toolbarContainerView = findViewById(R.id.toolbar_container);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null && getLoaderManager().getLoader(0) == null) { //avoid a refresh call if i just started the loader
            refresh();
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Get the transition name from the string
                    Intent intent = new Intent(Intent.ACTION_VIEW, ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    /*
                    String transitionName = getString(R.string.transition_string);
                    ImageView IMG = view.findViewById(R.id.thumbnail);
                    ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(ArticleListActivity.this, IMG, transitionName);
                    */
                    startActivity(intent);
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }

        }

        public void createPaletteAsync(Bitmap bitmap, final CardView cardView, final TextView title, final TextView subtitle) {
            Palette p = Palette.from(bitmap).generate();
            // Use generated palette
            Palette.Swatch vibrant = p.getVibrantSwatch();
            Palette.Swatch lightVibrant = p.getLightVibrantSwatch();
            Palette.Swatch darkVibrant = p.getDarkVibrantSwatch();
            Palette.Swatch dominantSwatch = p.getDominantSwatch();
            Palette.Swatch mutedSwatch = p.getMutedSwatch();
            Palette.Swatch darkMutedSwatch = p.getDarkMutedSwatch();
            ObjectAnimator animation = null;
            if (vibrant != null) {
                animation = ObjectAnimator.ofInt(cardView, "backgroundColor", Color.WHITE, vibrant.getRgb());
                title.setTextColor(vibrant.getTitleTextColor());
                subtitle.setTextColor(vibrant.getBodyTextColor());
            } else if (lightVibrant != null) {
                animation = ObjectAnimator.ofInt(cardView, "backgroundColor", Color.WHITE, lightVibrant.getRgb());
                title.setTextColor(lightVibrant.getTitleTextColor());
                subtitle.setTextColor(lightVibrant.getBodyTextColor());
            } else if (darkVibrant != null) {
                animation = ObjectAnimator.ofInt(cardView, "backgroundColor", Color.WHITE, darkVibrant.getRgb());
                title.setTextColor(darkVibrant.getTitleTextColor());
                subtitle.setTextColor(darkVibrant.getBodyTextColor());
            } else if (dominantSwatch != null) {
                animation = ObjectAnimator.ofInt(cardView, "backgroundColor", Color.WHITE, dominantSwatch.getRgb());
                title.setTextColor(dominantSwatch.getTitleTextColor());
                subtitle.setTextColor(dominantSwatch.getBodyTextColor());
            } else if (mutedSwatch != null) {
                animation = ObjectAnimator.ofInt(cardView, "backgroundColor", Color.WHITE, mutedSwatch.getRgb());
                title.setTextColor(mutedSwatch.getTitleTextColor());
                subtitle.setTextColor(mutedSwatch.getBodyTextColor());
            } else if (darkMutedSwatch != null) {
                animation = ObjectAnimator.ofInt(cardView, "backgroundColor", Color.WHITE, darkMutedSwatch.getRgb());
                title.setTextColor(darkMutedSwatch.getTitleTextColor());
                subtitle.setTextColor(darkMutedSwatch.getBodyTextColor());
            }

            animation.setEvaluator(new ArgbEvaluator());
            animation.setDuration(1000);
            animation.start();
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);

            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }

            String url = mCursor.getString(ArticleLoader.Query.THUMB_URL);

            LoadCard loadCard = new LoadCard(url, holder.cardView, holder.titleView, holder.subtitleView);
            loadCard.execute();
            Picasso.get().load(url).into(holder.thumbnailView);


        }

        public class LoadCard extends AsyncTask<String, Void, Bitmap> {
            private String mUrl;
            private CardView cardView;
            private TextView title, subtitle;

            public LoadCard(String mUrl, CardView cardView, TextView title, TextView subtitle) {
                this.mUrl = mUrl;
                this.cardView = cardView;
                this.title = title;
                this.subtitle = subtitle;
            }

            @Override
            protected Bitmap doInBackground(String... params) {
                final String url = mUrl;
                // do stuff
                Bitmap bitmap = getBitmapFromURL(url);
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                // super.onPostExecute(bitmap);
                createPaletteAsync(bitmap, cardView, title, subtitle);
            }

            public Bitmap getBitmapFromURL(String src) {
                try {
                    URL url = new URL(src);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    Bitmap myBitmap = BitmapFactory.decodeStream(input);
                    return myBitmap;
                } catch (IOException e) {
                    // Log exception
                    return null;
                }
            }
        }


        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;
        public CardView cardView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
            cardView = (CardView) view.findViewById(R.id.cardView);
        }
    }
}

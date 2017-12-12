package alizarchik.alex.photogallery.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import alizarchik.alex.photogallery.work_with_server.FlickrFetchr;
import alizarchik.alex.photogallery.model.GalleryItem;
import alizarchik.alex.photogallery.broadcasts_and_services.PollService;
import alizarchik.alex.photogallery.dbs.QueryPreferences;
import alizarchik.alex.photogallery.R;
import alizarchik.alex.photogallery.work_with_server.ThumbnailDownloader;

/**
 * Created by aoalizarchik.
 */

public class PhotoGalleryFragment extends VisibleFragment {

    private static final String TAG = "PhotoGalleryFragment";

    RecyclerView mRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener((photoHolder, bitmap) -> {
            Drawable drawable = new BitmapDrawable(getResources(), bitmap);
            photoHolder.bindDrawable(drawable);
        });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                mItems.clear();
                FlickrFetchr.setPAGE(String.valueOf(1));
                updateItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnSearchClickListener(v -> {
            String query = QueryPreferences.getStoredQuery(getActivity());
            searchView.setQuery(query, false);
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if (PollService.isServiceAlarmOn(getActivity())) {
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                mItems.clear();
                QueryPreferences.setStoredQuery(getActivity(), null);
                FlickrFetchr.setPAGE(String.valueOf(1));
                updateItems();
                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        String x = FlickrFetchr.getPAGE();
        int page = Integer.parseInt(x);
        if (page == 1) {
            new FetchItemsTask(query).execute();
        } else {
            page += 1;
            FlickrFetchr.setPAGE(String.valueOf(page));
            new FetchItemsTask(query).execute();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mRecyclerView = v.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        mRecyclerView.addOnScrollListener(new CustomScrollListener());

        setupAdapter();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private void setupAdapter() {
        if (isAdded()) {
            mRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        private ImageView mImageView;
        private GalleryItem mGalleryItem;

        PhotoHolder(View itemView) {
            super(itemView);
            mImageView = itemView.findViewById(R.id.image_view);
            itemView.setOnClickListener(this);
        }

        void bindDrawable(Drawable drawable) {
            mImageView.setImageDrawable(drawable);
        }

        void bindGalleryItem(GalleryItem galleryItem) {
            mGalleryItem = galleryItem;
        }

        @Override
        public void onClick(View v) {
            Intent i = PhotoPageActivity.newIntent(getActivity(), mGalleryItem.getPhotoPageUri());
            startActivity(i);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            holder.bindGalleryItem(galleryItem);
            Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);
            holder.bindDrawable(placeholder);
            mThumbnailDownloader.queueThumbnail(holder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchItemsTask extends AsyncTask<String, Void, List<GalleryItem>> {

        private String mQuery;

        FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(String... voids) {
            Log.d(TAG, "Search: " + mQuery);
            if (mQuery == null) {
                return new FlickrFetchr().fetchRecentPhotos();
            } else {
                return new FlickrFetchr().searchPhotos(mQuery);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            String x = FlickrFetchr.getPAGE();
            int page = Integer.parseInt(x);
            if (page == 1) {
                mItems.clear();
                mItems = galleryItems;
                setupAdapter();
            } else {
                GridLayoutManager layoutManager = GridLayoutManager.class.cast(mRecyclerView.getLayoutManager());
                int size = mItems.size() - 12;
                mItems.addAll(galleryItems);
                setupAdapter();
                layoutManager.scrollToPosition(size);
            }

        }
    }

    public class CustomScrollListener extends RecyclerView.OnScrollListener {

        private void endlessRecycler() throws InterruptedException {
            String query = QueryPreferences.getStoredQuery(getActivity());
            String x = FlickrFetchr.getPAGE();
            int page = Integer.parseInt(x);
            page += 1;
            FlickrFetchr.setPAGE(String.valueOf(page));
            FetchItemsTask fetchItemsTask = new FetchItemsTask(query);
            fetchItemsTask.execute();
        }

        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {

            super.onScrollStateChanged(recyclerView, newState);

            GridLayoutManager layoutManager = GridLayoutManager.class.cast(recyclerView.getLayoutManager());
            int visibleItemCount = layoutManager.getChildCount();
            int totalItemCount = layoutManager.getItemCount();
            int pastVisibleItems = layoutManager.findFirstCompletelyVisibleItemPosition();


            switch (newState) {
                case RecyclerView.SCROLL_STATE_IDLE:
                    System.out.println("The RecyclerView is not scrolling");
                    Log.i(TAG, "The RecyclerView is not scrolling");
                    if (pastVisibleItems + visibleItemCount >= totalItemCount) {
                        Toast.makeText(getActivity().getApplicationContext(), "Next page is loading", Toast.LENGTH_SHORT).show();
                        try {
                            endlessRecycler();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        // End of the list is here.
                        Log.i(TAG, "End of list");
                    }
                    break;
            }
        }
    }
}

//        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//            if (dx > 0) {
//                System.out.println("Scrolled Right");
//            } else if (dx < 0) {
//                System.out.println("Scrolled Left");
//            } else {
//                System.out.println("No Horizontal Scrolled");
//            }

//            if (dy == 0) {
//                System.out.println("Scrolled Downwards");
//                Log.i(TAG, "Scrolled Downwards");
//                movingDown = true;
//                if (fingerIsMoving && noScrollingIsDone && movingDown) {
//                    endlessRecycler();
//                } else {
//                    fingerIsMoving = false;
//                    noScrollingIsDone = false;
//                    movingDown = false;
//                }

//                String x = FlickrFetchr.getPAGE();
//                int page = Integer.parseInt(x);
//                page += 1;
//                FlickrFetchr.setPAGE(String.valueOf(page));
//                new FetchItemsTask().execute();
//            }
//            else if (dy < 0) {
//                System.out.println("Scrolled Upwards");
//            } else {
//                System.out.println("No Vertical Scrolled");
//            }
//        }
//        }
//    }
//}


//    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
//
//        super.onScrollStateChanged(recyclerView, newState);
//
//        GridLayoutManager layoutManager = GridLayoutManager.class.cast(recyclerView.getLayoutManager());
//        int visibleItemCount = layoutManager.getChildCount();
//        int totalItemCount = layoutManager.getItemCount();
//        int pastVisibleItems = layoutManager.findFirstCompletelyVisibleItemPosition();
//
//
//
//        switch (newState) {
//            case RecyclerView.SCROLL_STATE_IDLE:
//                System.out.println("The RecyclerView is not scrolling");
//                Log.i(TAG, "The RecyclerView is not scrolling");
//                if (noScrollingIsDone) {
//                    if (pastVisibleItems + visibleItemCount >= totalItemCount) {
//                        Toast.makeText(getActivity().getApplicationContext(), "Next page is loading", Toast.LENGTH_SHORT).show();
//                        try {
//                            endlessRecycler();
//                        } catch (InterruptedException e) {
//                            noScrollingIsDone = false;
//                            e.printStackTrace();
//                        }
//
//                        // End of the list is here.
//                        Log.i(TAG, "End of list");
//                    }
//                }
//                break;
//                case RecyclerView.SCROLL_STATE_DRAGGING:
//                    System.out.println("Scrolling now");
//                    Log.i(TAG, "Scrolling now");
//                    fingerIsMoving = true;
//                    break;
//                case RecyclerView.SCROLL_STATE_SETTLING:
//                    System.out.println("Scroll Settling");
//                    break;
//        }
//    }

//    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//            if (dx > 0) {
//                System.out.println("Scrolled Right");
//            } else if (dx < 0) {
//                System.out.println("Scrolled Left");
//            } else {
//                System.out.println("No Horizontal Scrolled");
//            }

//            if (dy == 0) {
//                System.out.println("Scrolled Downwards");
//                Log.i(TAG, "Scrolled Downwards");
//                movingDown = true;
//                if (fingerIsMoving && noScrollingIsDone && movingDown) {
//                    endlessRecycler();
//                } else {
//                    fingerIsMoving = false;
//                    noScrollingIsDone = false;
//                    movingDown = false;
//                }

//                String x = FlickrFetchr.getPAGE();
//                int page = Integer.parseInt(x);
//                page += 1;
//                FlickrFetchr.setPAGE(String.valueOf(page));
//                new FetchItemsTask().execute();
//            }
//            else if (dy < 0) {
//                System.out.println("Scrolled Upwards");
//            } else {
//                System.out.println("No Vertical Scrolled");
//            }
//        }
//    }
//}

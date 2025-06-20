package com.seafile.seadroid2.framework.datastore;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.os.EnvironmentCompat;

import com.blankj.utilcode.util.PathUtils;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.framework.datastore.sp.AppDataManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class decides where to store Seadroid's data in the file system.
 * <p>
 * Up till API 18, there was not much choice. Only CLASSIC_LOCATION did make any sense
 * to use. Starting with KitKat new options have appeared. API 19+ offers an API to get a list
 * of possible storage locations, which will include things like SD cards or USB connected flash
 * drives.
 * <p>
 * This StorageManager allows the user to make the choice where to store its data. It remembers
 * this choice in the SettingsManager.
 * <p>
 * This StorageManager also does an auto-selection of the best storage on first use of Seadroid.
 * <p>
 * The following data falls into the scope of this StorageManager:
 * <p>
 * - Downloaded repository files
 * -> Indexed by Gallery
 * -> synced server content, might be deleted locally
 * -> persistent, as deletion would cause user inconvenience
 * <p>
 * - Temp files (e.g. pending for upload, download in progress)
 * -> NOT indexed by Gallery
 * -> important content, not to be deleted prematurely
 * -> only temporary
 * -> might be moved to "Downloaded repository files", so should be same mount point
 * <p>
 * - Image thumbnails
 * -> NOT indexed by Gallery
 * -> long term storage
 * -> not important content, can be deleted if necessary
 * <p>
 * - JSON Cache files
 * -> NOT indexed by Gallery
 * -> long term storage
 * -> not important content, can be deleted if necessary
 * <p>
 * This class offers a set of methods to retrieve the base dir for specific types of data.
 * <p>
 * Useful links:
 * - https://developer.android.com/guide/topics/data/data-storage.html
 */
public abstract class StorageManager implements MediaScannerConnection.OnScanCompletedListener {

    protected static final String DEBUG_TAG = "StorageManager";

    private static StorageManager instance = null;

    private final Location CLASSIC_LOCATION;

    public StorageManager() {
        CLASSIC_LOCATION = buildClassicLocation();
    }

    public static StorageManager getInstance() {
        if (instance == null) {
            instance = new StorageManagerLollipop();
        }
        return instance;
    }

    private Location buildClassicLocation() {
        Location classic = new Location();
        classic.id = -1; // Android IDs start at 0. so "-1" is safe for us

        File[] externalMediaDirs = SeadroidApplication.getAppContext().getExternalMediaDirs();
        String rootPath = externalMediaDirs[0].getAbsolutePath();
        classic.mediaPath = new File(rootPath + "/Seafile/");

        //
        String appCachePath = PathUtils.getExternalAppCachePath();
        classic.cachePath = new File(appCachePath);


//        classic.externalImagePath = Environment.DIRECTORY_DCIM+"";

        fillLocationInfo(classic);
        return classic;
    }

    private void fillLocationInfo(Location loc) {
        loc.available = loc.mediaPath != null && EnvironmentCompat.getStorageState(loc.mediaPath).equals(Environment.MEDIA_MOUNTED);

        String label;
        // labels "primary/secondary" are as defined by https://possiblemobile.com/2014/03/android-external-storage/
        if (loc.id <= 0) {
            label = getContext().getString(R.string.storage_manager_primary_storage);
        } else {
            label = getContext().getString(R.string.storage_manager_secondary_storage);
        }
        if (loc.available) {
            loc.description = getContext().getString(R.string.storage_manager_storage_description,
                    label,
                    Formatter.formatFileSize(getContext(), getStorageFreeSpace(loc.mediaPath)),
                    Formatter.formatFileSize(getContext(), getStorageSize(loc.mediaPath)));
        } else {
            loc.description = getContext().getString(R.string.storage_manager_storage_description_not_available,
                    label);
        }
    }

    /**
     * Get the media directories offered by Android.
     *
     * @return
     */
    protected abstract File[] getMediaCacheDirs();

    /**
     * Get the cache directories offered by Android.
     *
     * @return
     */
    protected abstract File[] getAppCacheDir();

    /**
     * Get partition size of the mount point containing dir
     *
     * @param dir
     * @return
     */
    protected abstract long getStorageSize(File dir);

    /**
     * Get free size of the mount point containing dir
     *
     * @param dir
     * @return
     */
    protected abstract long getStorageFreeSpace(File dir);

    /**
     * Allows this device multiple storage locations?
     *
     * @return
     */
    public abstract boolean supportsMultipleStorageLocations();

    /**
     * Callback when a file has been added/removed by us in the Android Media Store
     *
     * @param path
     * @param uri
     */
    public void onScanCompleted(String path, Uri uri) {
    }

    public final ArrayList<Location> getStorageLocations() {
        ArrayList<Location> retList = new ArrayList<>();

        int selectedDir = AppDataManager.readStorageDir();

        retList.add(CLASSIC_LOCATION);

        File[] mediaDirs = getMediaCacheDirs();
        File[] cacheDir = getAppCacheDir();
        for (int i = 0; i < mediaDirs.length; i++) {

            // omit the mount point where CLASSIC_LOCATION lies (would be duplicate)
            if (i == 0)
                continue;

            Location location = new Location();
            location.id = i;
            location.mediaPath = mediaDirs[i];
            location.cachePath = cacheDir[i];
            retList.add(location);
        }

        // add current selection at the end, even if the storage is currently unavailable
        if (selectedDir != CLASSIC_LOCATION.id && selectedDir >= mediaDirs.length) {
            Location location = new Location();
            location.id = selectedDir;
            location.mediaPath = null;
            location.cachePath = null;
            retList.add(location);
        }

        for (Location loc : retList) {
            loc.currentSelection = (loc.id == selectedDir);
            fillLocationInfo(loc); // fill in size & description info
        }

        return retList;
    }

    /**
     * Set the new storage directory.
     * <p>
     * This will change the settings and move files from the old to the new location.
     * Therefore, this method might take a while to finish.
     *
     * @param id the ID of the new storage location
     */
    public final void setStorageDir(int id) {

        int oldID = AppDataManager.readStorageDir();

        if (oldID == id)
            return;

        Location newLocation = lookupStorageLocation(id);

        File newMediaDir = newLocation.mediaPath;

        if (newLocation.mediaPath == null || !EnvironmentCompat.getStorageState(newMediaDir).equals(Environment.MEDIA_MOUNTED)) {
            Log.i(DEBUG_TAG, "Selected storage dir is unavailable! " + newMediaDir);
            return;
        }

        Location oldLocation = lookupStorageLocation(oldID);
        if (oldLocation != null) {

            try {
                // move cached files from old location to new location (might take a while)
                List<Account> list = SupportAccountManager.getInstance().getAccountList();
                for (Account account : list) {
                    String accDir = DataManager.getAccountDir(account);
                    File oldAccountDir = new File(accDir);

                    if (oldAccountDir.isDirectory()) {
                        FileUtils.copyDirectoryToDirectory(oldAccountDir, newMediaDir);
                    }
                }
                notifyAndroidGalleryDirectoryChange(FileUtils.listFiles(newMediaDir, null, true));

            } catch (Exception e) {
                Log.e(DEBUG_TAG, "Could not move cache to new location", e);
                return;
            }

            // remove everything in the old cache directories (thumbnails, etc).
            clearCache();
        }

        Log.i(DEBUG_TAG, "Setting storage directory to " + newMediaDir);
        AppDataManager.writeStorageDir(id);
    }

    /**
     * Decide, which storage to use. This will be called only once,
     * on first start of Seadroid. After that, it's read from the Settings.
     * <p>
     * -> API 19+
     * It selects the media with the most free space in it.
     * <p>
     * -> API 1-18
     * On pre-KitKat, only CLASSIC_LOCATION is available. So there is no choice.
     * <p>
     * This method does not change the Settings. It just evaluates what the best storage location
     * might be.
     *
     * @return storage ID with the most free space
     */
    private Location getPreferredStorage() {

        /* Backwards compatibility on upgrade:
         * If there is already CLASSIC_LOCATION present on the system, prefer it
         */
        if (CLASSIC_LOCATION.mediaPath.exists() && CLASSIC_LOCATION.mediaPath.isDirectory()) {

            return CLASSIC_LOCATION;

        } else {

            // auto-select the location with the most free space available

            Location best = null;
            for (Location location : getStorageLocations()) {
                if (!location.available)
                    continue;

                if (best == null || getStorageFreeSpace(best.mediaPath) < getStorageFreeSpace(location.mediaPath))
                    best = location;
            }

            if (best == null)
                return CLASSIC_LOCATION;

            return best;
        }
    }

    /**
     * Return the base directory for media storage to be used in Seadroid.
     * <p>
     * It guaranties to always return a valid directory.
     * However, this directory might change at runtime, So it should never be cached.
     *
     * @return the base directory for media storage to be used in Seadroid.
     */
    public final File getMediaDir() {
        return getDirectoryCreateIfNeeded(getStorageLocation().mediaPath);
    }

    private Location lookupStorageLocation(int id) {
        for (Location location : getStorageLocations()) {
            if (location.id == id)
                return location;
        }
        return null;
    }

    /**
     * Return the storage location to be used in Seadroid.
     * <p>
     * It guaranties to always return a valid one.
     *
     * @return Location info
     */
    public Location getStorageLocation() {

        int storageDirID = AppDataManager.readStorageDir();
        Location storageLocation = lookupStorageLocation(storageDirID);

        // if there is one configured but unavailable, use fallback
        if (storageDirID >= 0 && !storageLocation.available) {
            Log.i(DEBUG_TAG, "Configured storage location " + storageDirID + " has become unavailable, falling back.");
            return CLASSIC_LOCATION;
        }

        // on first start of Seadroid, no location is configured yet
        if (storageDirID == Integer.MIN_VALUE) {

            storageLocation = getPreferredStorage();

            Log.i(DEBUG_TAG, "First start of Seadroid, auto-setting storage directory to " + storageLocation.id);
            AppDataManager.writeStorageDir(storageLocation.id);
        }

        if (storageLocation == null || !storageLocation.available) {
            Log.i(DEBUG_TAG, "Storage location " + storageLocation.id + " has become unavailable, falling back.");
            return CLASSIC_LOCATION;
        }

        // an explicit path is configured
        return storageLocation;
    }

    private File getDirectoryCreateIfNeeded(File dir) {
        if (dir.exists()) {
            return dir;
        } else {
            dir.mkdirs();
        }
        return dir;
    }

    protected final Context getContext() {
        return SeadroidApplication.getAppContext();
    }

    /**
     * Store temp files in a subdirectory below the media directory.
     * <p>
     * This should be a subdirectory of getMediaDir() so that temp files
     * can be efficiently moved into the media storage.
     *
     * @return base of where to store temp files
     */
    public final File getTempDir() {
        File base = getStorageLocation().cachePath;
        File tmpDir = new File(base, ".temp");
        return getDirectoryCreateIfNeeded(tmpDir);
    }

    public final File getLogDir() {
        File base = getStorageLocation().cachePath;
        File f = new File(base, "logs");
        return getDirectoryCreateIfNeeded(f);
    }

    public final File getGlideCacheDir() {
        File base = getStorageLocation().cachePath;
        File f = new File(base, "glide");
        return getDirectoryCreateIfNeeded(f);
    }

    public final File getTakeCameraDir() {
        File base = getStorageLocation().cachePath;
        File f = new File(base, "camera");
        return getDirectoryCreateIfNeeded(f);
    }

    /**
     * Store JSON cache files in private internal cache.
     * <p>
     * This cache directory will contain json files listing the repositories and directory listings.
     * this can be pretty private (especially the repository listing). So these should not be readable
     * by other apps. Therefore we save them in internal storage, where only Seadroid has access to.
     *
     * @return base of where to store JSON cache files
     */
    public final File getJsonCacheDir() {
        File base = getContext().getCacheDir();
        return getDirectoryCreateIfNeeded(base);
    }

    /**
     * A file was added, changed or removed. Notify the gallery.
     *
     * @param file
     */
    public final void notifyAndroidGalleryFileChange(File file) {
        MediaScannerConnection.scanFile(getContext(),
                new String[]{file.toString()},
                null,
                this);
    }

    /**
     * A directory was added, changed or removed. Notify the gallery.
     *
     * @param fileList
     */
    private void notifyAndroidGalleryDirectoryChange(Collection<File> fileList) {

        int count = 0;
        String[] list = new String[fileList.size()];
        for (File f : fileList) {
            list[count++] = f.getAbsolutePath();
        }

        MediaScannerConnection.scanFile(getContext(), list, null, this);
    }

    /**
     * Deletes full cache
     * remember to clear cache from database after called this method
     */
    public final void clearCache() {
        Collection<File> fileList = FileUtils.listFiles(getMediaDir(), null, true);

        FileUtils.deleteQuietly(getMediaDir());
        FileUtils.deleteQuietly(getJsonCacheDir());
        FileUtils.deleteQuietly(getTempDir());

        notifyAndroidGalleryDirectoryChange(fileList);
    }

    /**
     * Deletes cache directory under a specific account<br>
     * remember to clear cache from database after called this method
     */
    public final void clearAccount(Account account) {
        String accDir = DataManager.getAccountDir(account);
        File accountDir = new File(accDir);
        Collection<File> fileList = FileUtils.listFiles(accountDir, null, true);

        FileUtils.deleteQuietly(accountDir);

        notifyAndroidGalleryDirectoryChange(fileList);
    }

    /**
     * Return space used by Seadroid
     *
     * @return
     */
    public final long getUsedSpace() {
        File mediaDir = getMediaDir();

        if (!mediaDir.exists()) return 0L;

        return FileUtils.sizeOfDirectory(mediaDir);
    }

    public static class Location {
        /**
         * Our internal ID of this storage.
         */
        public int id;

        /**
         * Base media directory
         */
        public File mediaPath;

        /**
         * Base cache directory
         */
        public File cachePath;

        /**
         * Store photos taken by the camera
         */
        public File externalImagePath;

        /**
         * Store videos taken by the camera
         */
        public File externalVideoPath;

        /**
         * Text description
         */
        public String description;

        /**
         * Is this location available (mounted)?
         */
        public boolean available;

        /**
         * Is this the currently selected location?
         */
        public boolean currentSelection;
    }
}

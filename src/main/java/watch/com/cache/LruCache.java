package watch.com.cache;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实现LRU缓存系统.
 */
public class LruCache<K, V> implements Cache<K, V> {
    /**
     * 默认内存系统大小为最大内存的1/8.
     */
    private static final int DEFAULT_CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 8);

    /**
     * 存储数据结构.
     */
    private final HashMap<K, V> mMap;

    /**
     * 当前内存系统的最大值.
     */
    private final int mMaxSize;

    /**
     * 当前内存系统的存储容量.
     */
    private int mSize;

    public LruCache() {
        this(DEFAULT_CACHE_SIZE);
    }

    public LruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxsize <= 0");
        }

        this.mMaxSize = maxSize;
        mMap = new LinkedHashMap<>(0, 0.75f, true);
    }

    @Override
    public V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        synchronized (this) {
            V value = mMap.get(key);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    @Override
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        V previous;
        synchronized (this) {
            mSize += safeSizeOf(key, value);
            previous = mMap.put(key, value);
            if (previous != null) {
                mSize -= safeSizeOf(key, previous);
            }

            trimToSize();
        }

        return previous;
    }

    private void trimToSize() {
        while (true) {
            // 处理异常情况
            if (mSize < 0 || (mMap.isEmpty() && mSize != 0)) {
                throw new IllegalStateException(getClass().getName()
                        + ".sizeOf() is reporting inconsistent results!");
            }

            // 如果当前容量小于最大容量,则直接退出循环返回即可.
            if (mSize <= mMaxSize || mMap.isEmpty()) {
                break;
            }

            Map.Entry<K, V> removeEntry = mMap.entrySet().iterator().next();
            mSize -= sizeOf(removeEntry.getKey(), removeEntry.getValue());
            mMap.remove(removeEntry.getKey());
        }
    }

    @Override
    public V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V previous;
        synchronized (this) {
            previous = mMap.get(key);
            if (previous != null) {
                mSize -= sizeOf(key, previous);
            }
        }

        return previous;
    }

    @Override
    public void clear() {
        synchronized (this) {
            mMap.clear();
            mSize = 0;
        }
    }


    private int safeSizeOf(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }

    /**
     * Returns the size of the entry for {@code key} and {@code value} in
     * user-defined units.  The default implementation returns 1 so that size
     * is the number of entries and max size is the maximum number of entries.
     *
     * <p>An entry's size must not change while it is in the cache.
     */
    @SuppressWarnings("unused")
    protected int sizeOf(K key, V value) {
        return 1;
    }

    @Override public synchronized final String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (Map.Entry<K, V> entry : mMap.entrySet()) {
            sBuilder.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append(";");
        }

        sBuilder.append("MaxMemory=").append(mMaxSize).append(", currentSize=").append(mSize);

        return sBuilder.toString();
    }
}

package watch.com.cache;

/**
 * 定义缓存系统统一接口
 */
public interface Cache<K, V> {
    V get(K key);

    V put(K key, V value);

    V remove(K key);

    void clear();
}

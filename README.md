# LruCache

LruCache是基于Android SDK中的LruCache类参考实现的LRU算法缓存存储类.

------
# 原理

之前分析过Lru算法的实现方式:HashMap+双向链表,参考链接:[LRU算法&&LeetCode解题报告](http://blog.csdn.net/wzy_1988/article/details/33444991)

这里主要介绍Android SDK中LruCache缓存算法的实现.

------
## 构造函数

LruCache只有一个构造函数,并且有一个必传参数:
```java
public LruCache(int maxSize) {
    if (maxSize <= 0) {
        throw new IllegalArgumentException("maxSize <= 0");
    }
    // 初始化最大缓存大小.
    this.maxSize = maxSize;
    // 初始化LinkedHashMap.其中:
    // 1. initialCapacity, 初始大小.
    // 2. loadFactor, 负载因子.
    // 3. accessOrder, true:基于访问顺序排序, false:基于插入顺序排序.
    this.map = new LinkedHashMap<K, V>(0, 0.75f, true);
}
```

------
## get方法

LruCache的get方法源码如下:
```java
public final V get(K key) {
    // lru的key不能为null
    if (key == null) {
        throw new NullPointerException("key == null");
    }

    // get方法线程安全
    V mapValue;
    synchronized (this) {
        // LinkedHashMap每次get会基于访问顺序重新排序
        mapValue = map.get(key);
        if (mapValue != null) {
            // 命中,命中数+1,且返回命中数据
            hitCount++;
            return mapValue;
        }
        // 未命中数+1
        missCount++;
    }

    // 很奇葩,一般LruCache类都不会重写create方法的,所以下面的逻辑不需要care.
    V createdValue = create(key);
    if (createdValue == null) {
        return null;
    }

    synchronized (this) {
        createCount++;
        mapValue = map.put(key, createdValue);

        if (mapValue != null) {
            // There was a conflict so undo that last put
            map.put(key, mapValue);
        } else {
            size += safeSizeOf(key, createdValue);
        }
    }

    if (mapValue != null) {
        entryRemoved(false, key, createdValue, mapValue);
        return mapValue;
    } else {
        trimToSize(maxSize);
        return createdValue;
    }
}
```

从上述代码中,我们并没有看到LruCache算法的实现,即访问一个元素时,如果元素命中,则需要将元素保护起来,避免存储空间不够时立刻被删除.
其实,这块操作是由LinkedHashMap实现的,源码如下:
```java
@Override public V get(Object key) {
    // 不需要考虑key为null的情况,因为LruCache禁止key为null.
    if (key == null) {
        HashMapEntry<K, V> e = entryForNullKey;
        if (e == null)
            return null;
        if (accessOrder)
            makeTail((LinkedEntry<K, V>) e);
        return e.value;
    }

    // 根据key计算hash值,用于定位数组中的槽
    int hash = Collections.secondaryHash(key);
    HashMapEntry<K, V>[] tab = table;
    // HashMap的数据结构为: 数组 + 链表
    // 首先,根据key的hash值定义数组存储下标index.
    // 数组的每个元素是HashMapEntry,以链表的形式组织在一起,是一种解决冲突的方案.
    for (HashMapEntry<K, V> e = tab[hash & (tab.length - 1)];
            e != null; e = e.next) {
        K eKey = e.key;
        if (eKey == key || (e.hash == hash && key.equals(eKey))) {
            if (accessOrder)
                // 命中同时,accessOrder为true,代表基于访问顺序排序,需要将当前访问的节点移动到尾部,实现LRU的get算法精髓.
                makeTail((LinkedEntry<K, V>) e);
            return e.value;
        }
    }
    return null;
}
```

其中,makeTail的源码就是将当前访问的节点移动到链表尾部,非常简单的链表操作,注释源码如下:
```java
private void makeTail(LinkedEntry<K, V> e) {
    // 将e从链表中断开
    e.prv.nxt = e.nxt;
    e.nxt.prv = e.prv;

    // 获取当前链表的头部
    LinkedEntry<K, V> header = this.header;
    // 获取当前链表的尾部
    LinkedEntry<K, V> oldTail = header.prv;
    
    // 将e插入到oldTail之后,作为链表最新的尾部.
    e.nxt = header;
    e.prv = oldTail;
    oldTail.nxt = header.prv = e;
    modCount++;
}
```

**为什么将访问的节点移动到当前链表的尾部就能对该元素起到保护作用呢？**

答: 因为当Cache空间不够时,删除元素是从链表头部开始进行的.想要更具体的了解,就需要看一下LruCache的put函数源码了.

--------
## put方法

LruCache的put源码比较简单,注释源码如下:
```java
public final V put(K key, V value) {
    // 禁止存入的key或者value为null,否则抛出空指针异常.
    if (key == null || value == null) {
        throw new NullPointerException("key == null || value == null");
    }

    V previous;
    synchronized (this) {
        // 存放数+1
        putCount++;
        // 累加存储容量
        size += safeSizeOf(key, value);
        // 调用HashMap的put方法进行存储
        previous = map.put(key, value);
        if (previous != null) {
            // 如果是同一个key,value替换的情况,则需要减去该previous值的大小.
            size -= safeSizeOf(key, previous);
        }
    }

    if (previous != null) {
        entryRemoved(false, key, previous, value);
    }

    // 计算是否超size,如果超size,需要进行删除操作.
    trimToSize(maxSize);
    return previous;
}
```

通过源码我们发现put源码的实现只是调用了HashMap的put方法,HashMap的put方法实现机制:

1. 如果key本身存在,则替换value.
2. 如果key不存在,则使用头插法将键值对插入.

HashMap的put方法源码如下:
```java
@Override public V put(K key, V value) {
    if (key == null) {
        return putValueForNullKey(value);
    }
    
    // 计算key对应的hash值
    int hash = Collections.secondaryHash(key);
    HashMapEntry<K, V>[] tab = table;
    // 根据key的hash值获取key在数组中存储的index下标
    int index = hash & (tab.length - 1);
    // 首先遍历数组index槽中的链表,判断该key是否已经存储
    for (HashMapEntry<K, V> e = tab[index]; e != null; e = e.next) {
        if (e.hash == hash && key.equals(e.key)) {
            // key之前存在,直接替换value
            preModify(e);
            V oldValue = e.value;
            e.value = value;
            return oldValue;
        }
    }

    // key不存在,直接插入操作
    modCount++;
    // 扩容
    if (size++ > threshold) {
        tab = doubleCapacity();
        index = hash & (tab.length - 1);
    }
    // 头插法插入<key,value>键值对
    addNewEntry(key, value, hash, index);
    return null;
}
```

> Tips:
> 我认为这里LruCache的put方法是有问题的,没有对put元素进行保护.有知悉为什么Android不采用尾插法的同学可以留言指导我一下.

接下来,我们还需要关注一下trimToSize函数实现,这也是LRU缓存替换策略实现的关键.

### trimToSize方法

```java
public void trimToSize(int maxSize) {
    // 一个死循环,循环到容量不超过构造函数中定义的maxsize.
    while (true) {
        K key;
        V value;
        synchronized (this) {
            // 处理异常情况
            if (size < 0 || (map.isEmpty() && size != 0)) {
                throw new IllegalStateException(getClass().getName()
                        + ".sizeOf() is reporting inconsistent results!");
            }

            // 如果当前容量小于最大容量,则直接退出循环返回即可.
            if (size <= maxSize) {
                break;
            }

            // 获取对头元素,也是最近最久没访问过的元素(eldest为包访问权限)
            Map.Entry<K, V> toEvict = map.eldest();
            if (toEvict == null) {
                break;
            }

            key = toEvict.getKey();
            value = toEvict.getValue();
            // 删除元素,降低当前存储使用量
            map.remove(key);
            size -= safeSizeOf(key, value);
            evictionCount++;
        }

        entryRemoved(true, key, value, null);
    }
}
```

由于eldest有hide注解,为包访问权限,但是通过前面的分析,我们就可以知道eldest是获取对头元素:
```java
public Entry<K, V> eldest() {
    LinkedEntry<K, V> eldest = header.nxt;
    return eldest != header ? eldest : null;
}
```
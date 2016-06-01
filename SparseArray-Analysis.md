# SparseArray源码解析

Android官方推荐:当使用HashMap<K, V>,如果K为整数类型时,使用SparseArray的效率更高.

我们通过分析SparseArray的源码,来看一下为什么当K为整数类型时,使用SparseArray的效率更高.

------
# 构造函数

中文注释的源码如下:
```java
/**
 * 存储索引集合.
 */
private int[] mKeys;

/**
 * 存储对象集合.
 */
private Object[] mValues;

/**
 * 存储的键值对总数.
 */
private int mSize;

/**
 * 采用默认的构造函数,则初始容量为10.
 */
public SparseArray() {
    this(10);
}

/**
 * 使用指定的初始容量构造SparseArray.
 *
 * @param initialCapacity 初始容量
 */
public SparseArray(int initialCapacity) {
    if (initialCapacity == 0) {
        // Effective Java中第43条:返回零长度的数组或者集合,而不是:null
        mKeys = ContainerHelpers.EMPTY_INTS;
        mValues = ContainerHelpers.EMPTY_OBJECTS;
    } else {
        // 构造initialCapacity大小的int数组和object数组
        mKeys = new int[initialCapacity];
        mValues = new Object[initialCapacity];
    }

    // 设置SparseArray存储的<key,value>键值对个数为0.
    mSize = 0;
}
```

和HashMap的数据结构不同,HashMap是使用数组+链表的数据结构存储键值对,而SparseArray只是用了两个数组进行存储.

------
# ContainerHelpers类

之所以SparseArray在存储key为整形的键值对的效率高于HashMap,很大一部分原因是整形key的查找过程中ContainerHelpers类提供了二分查找算法,从而降低了时间复杂度.
接下来,我们分析一下ContainerHelpers类的二分查找算法实现:
```java
class ContainerHelpers {

    // This is Arrays.binarySearch(), but doesn't do any argument validation.
    static int binarySearch(int[] array, int size, int value) {
        // 获取二分的起始和结束下标.
        int lo = 0;
        int hi = size - 1;

        while (lo <= hi) {
            // 获取中点的下标和值
            final int mid = (lo + hi) >>> 1;
            final int midVal = array[mid];

            if (midVal < value) {
                lo = mid + 1;
            } else if (midVal > value) {
                hi = mid - 1;
            } else {
                return mid;  // value found
            }
        }
        return ~lo;  // value not present
    }
}
```
这个二分算法的精髓在于:当二分查找没有找到相应元素时,返回的是lo值取反.

因为正数取反为负数,binarySearch返回正数代表查找成功,返回负数代表查找失败.
同时,binarySearch查找失败后,lo代表了当前元素按照升序排序应该插入的下标,后续取反后可以直接获取插入位置.

-------
# put()函数

put函数的中文注释源码如下:
```java
/**
 * 在SparseArray中存储键值对.
 */
public void put(int key, E value) {
    // 通过二分查找算法计算索引
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i >= 0) {
        // key已经存在对应的value,则直接替换value.
        mValues[i] = value;
    } else {
        i = ~i;

        if (i < mSize && mValues[i] == DELETED) {
            // 特殊的case,直接存储key-value即可
            mKeys[i] = key;
            mValues[i] = value;
            return;
        }

        if (mGarbage && mSize >= mKeys.length) {
            // 如果有元素被删除,并且目前容量不足,先进行一次gc
            gc();

            // Search again because indices may have changed.
            i = ~ContainerHelpers.binarySearch(mKeys, mSize, key);
        }

        // 扩容
        if (mSize >= mKeys.length) {
            // 获取扩容的数组大小
            int n = mSize + 1;

            int[] nkeys = new int[n];
            Object[] nvalues = new Object[n];

            // 数组拷贝最好使用System.arraycopy,而不是自己重撸一遍
            System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
            System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

            mKeys = nkeys;
            mValues = nvalues;
        }

        // i为插入位置,如果i<mSize,则i之后的元素需要依次向后移动一位.
        if (mSize - i != 0) {
            System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i);
            System.arraycopy(mValues, i, mValues, i + 1, mSize - i);
        }

        // 设置值,存储数量+1
        mKeys[i] = key;
        mValues[i] = value;
        mSize++;
    }
}
```

通过源码,我们来总结一下put函数的步骤:

1. 通过二分查找算法,计算key的索引值.
2. 如果索引值大于0,说明有key对应的value存在,直接替换value即可.
3. 如果索引值小于0,对索引值取反,获取key应该插入的坐标i.
4. 判断是否需要扩容:1.需要扩容,则先扩容; 2.不需要扩容,则利用System.arraycopy移动相应的元素,进行<key,value>键值对插入.

------
# get()函数

get函数就是利用二分查找获取key的下标,然后从object[] value数组中根据下标获取值.
之所以SparseArray号称比HashMap有更好的性能:

1. SparseArray更加节约内存,一个int[]数组存储所有的key,一个object[] 数组存储所有的value.
2. HashMap遇到冲突时,时间复杂度为O(n).而SparseArray不会有冲突,采用二分搜索算法,时间复杂度为O(lgn).

中文注释源码:
```java
/**
 * 根据指定的key获取value.
 */
public E get(int key) {
    return get(key, null);
}

/**
 * 利用二分查找算法根据key获取指定的value.
 */
public E get(int key, E valueIfKeyNotFound) {
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i < 0 || mValues[i] == DELETED) {
        return valueIfKeyNotFound;
    } else {
        return (E) mValues[i];
    }
}
```

------
# delete()函数

SparseArray中,remove函数最终也是调用delete函数进行<K,V>删除的.
而delete操作只是根据二分算法查找出key对应的下标,然后将object[] value中的对应下标值设置为DELETED.

源码如下:
```java
/**
 * 根据key删除指定的value.
 */
public void delete(int key) {
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i >= 0) {
        if (mValues[i] != DELETED) {
            // 标记i的值为private static final Object DELETED = new Object();
            mValues[i] = DELETED;
            // 设置gc标记为true.
            mGarbage = true;
        }
    }
}

/**
 * Alias for {@link #delete(int)}.
 */
public void remove(int key) {
    delete(key);
}
```

------
# gc()函数

delete()函数中将被删除的key对应的value设置为DELETED,并设置gc标志mGarbage为ture,那SparseArray是神马时候执行gc的呢？
如果仔细看了上面的文章,应该还是会有印象.SparseArray是在put函数的时候执行了gc:
```java
if (mGarbage && mSize >= mKeys.length) {
    // 如果有元素被删除,并且目前容量不足,先进行一次gc
    gc();

    // Search again because indices may have changed.
    i = ~ContainerHelpers.binarySearch(mKeys, mSize, key);
}
```

gc的实现如下:
```java
private void gc() {
    int n = mSize;
    int o = 0;
    int[] keys = mKeys;
    Object[] values = mValues;

    for (int i = 0; i < n; i++) {
        Object val = values[i];

        if (val != DELETED) {
            if (i != o) {
                keys[o] = keys[i];
                values[o] = val;
                values[i] = null;
            }

            o++;
        }
    }

    mGarbage = false;
    mSize = o;
}
```

gc函数的原理:遍历一遍数组,将非DELETED资源全部移动到数组前面.

------
# 小结

SparseArray<E> vs HashMap<Integer, E>:

1. 首先,这是两种完全不同的数据结构.SparseArray是两个数组:int[]和Object[], HashMap是数组+链表.
2. 查找效率上: 首先,SparseArray不需要对key进行hash运算,并且通过二分查找保证查询效率为O(lgn).而HashMap在未冲突的情况下是O(1),冲突的情况下是O(n).





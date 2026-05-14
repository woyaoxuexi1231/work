package work.N1javabasic.v1.day1;

public class MyArrayList {
    private Object[] elements;
    private int size;
    
    private static final int DEFAULT_CAPACITY = 10;
    
    public MyArrayList() {
        this.elements = new Object[DEFAULT_CAPACITY];
        this.size = 0;
    }
    
    public void add(Object element) {
        if (size == elements.length) {
            grow();
        }
        elements[size++] = element;
    }
    
    public Object get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return elements[index];
    }
    
    public Object remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        
        Object oldValue = elements[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        elements[--size] = null;
        return oldValue;
    }
    
    private void grow() {
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1); // 1.5倍扩容
        
        // 打印扩容关键信息
        System.out.println("扩容触发: 旧容量=" + oldCapacity + 
                          ", 新容量=" + newCapacity + 
                          ", 当前元素数量=" + size);
        
        Object[] newElements = new Object[newCapacity];
        System.arraycopy(elements, 0, newElements, 0, size);
        elements = newElements;
    }
    
    public int size() {
        return size;
    }
    
    public static void main(String[] args) {
        MyArrayList list = new MyArrayList();
        
        System.out.println("开始插入20个元素...");
        for (int i = 0; i < 20; i++) {
            list.add("元素" + i);
        }
        System.out.println("插入完成，最终大小: " + list.size());
    }
}
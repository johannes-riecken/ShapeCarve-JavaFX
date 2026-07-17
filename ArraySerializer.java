import java.io.*;

public class ArraySerializer {
    public static void main(String[] args) throws IOException {
        // any content works here, as we're only interested in
        // serial_version_uid, etc.
        int[][][] a = new int[][][]{{{1, 2}, {3, 4}}, {{5, 6}, {7, 8}}};
        FileOutputStream f = new FileOutputStream("3dArray.ser");
        ObjectOutput s = new ObjectOutputStream(f);
        s.writeObject(a);
        s.flush();
    }
}

package dslab.util;

import java.io.OutputStream;
import java.io.PrintWriter;

public class Writer {

    private PrintWriter writer;

    public Writer(OutputStream out) {
        this.writer = new PrintWriter(out);
    }

    public void write(String msg) {
        writer.println(msg);
        writer.flush();
    }


    public void shut() {
        writer.close();
    }
}

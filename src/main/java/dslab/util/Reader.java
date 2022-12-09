package dslab.util;

import java.io.*;

public class Reader {
    private BufferedReader reader;

    public Reader(InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in));
    }

    public String read() throws IOException {
        return reader.readLine();
    }

    public void shut() {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Error while shutting down reader: ", e);
        }
    }

}

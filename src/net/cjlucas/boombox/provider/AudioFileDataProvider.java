package net.cjlucas.boombox.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class AudioFileDataProvider extends AudioDataProvider
{
    private File file;
    private FileInputStream inStream;

    public AudioFileDataProvider(File file)
    {
        this.file = file;
    }

    public boolean prepare()
    {
        try {
            this.inStream = new FileInputStream(this.file);
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public int onNeedData(byte[] buffer)
    {
        try {
            int size = this.inStream.read(buffer);
            return size;
        } catch (IOException e) {
            return STATUS_ERROR_OCCURED;
        }
    }

    public void release()
    {
        if (this.inStream == null) {
            return;
        }

        try {
            this.inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

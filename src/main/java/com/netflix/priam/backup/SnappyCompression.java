package com.netflix.priam.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

/**
 * Class to generate compressed chunks of data from either an input stream or a
 * RandomAccessFile
 */
public class SnappyCompression
{
    private static final int BUFFER = 2 * 1024;
    private static final int MAX_CHUNKS = 10000;

    public void decompress(InputStream input, OutputStream output) throws IOException
    {

        SnappyInputStream is = new SnappyInputStream(new BufferedInputStream(input));
        byte d[] = new byte[BUFFER];
        BufferedOutputStream dest1 = new BufferedOutputStream(output, BUFFER);
        try
        {
            int c;
            while ((c = is.read(d, 0, BUFFER)) != -1)
            {
                dest1.write(d, 0, c);
            }
        }
        finally
        {
            IOUtils.closeQuietly(dest1);
            IOUtils.closeQuietly(is);
        }
    }

    public void decompressAndClose(InputStream input, OutputStream output) throws IOException
    {
        try
        {
            decompress(input, output);
        }
        finally
        {
            IOUtils.closeQuietly(input);
            IOUtils.closeQuietly(output);
        }
    }

    /**
     * Return an iterator for compressed data parts from a RandomAccessFile.
     * File is read only while iterating.
     * 
     * @param fis
     * @param chunkSize
     * @return
     * @throws IOException
     */
    public Iterator<byte[]> compress(RandomAccessFile fis, long chunkSize) throws IOException
    {
        return new ChunkedRAFStream(fis, chunkSize);
    }

    /**
     * Similar to compress. Reads from the specified InputStream
     * @param is
     * @param chunkSize
     * @return
     * @throws IOException
     */
    public Iterator<byte[]> compress(InputStream is, long chunkSize) throws IOException
    {
        return new ChunkedStream(is, chunkSize);
    }

    public class ChunkedStream implements Iterator<byte[]>
    {
        private boolean hasnext = true;
        private ByteArrayOutputStream bos;
        private SnappyOutputStream compress;
        private InputStream origin;
        private long chunkSize;

        public ChunkedStream(InputStream is, long chunkSize) throws IOException
        {
            this.origin = is;
            this.bos = new ByteArrayOutputStream();
            this.compress = new SnappyOutputStream(bos);
            this.chunkSize = chunkSize;
        }

        @Override
        public boolean hasNext()
        {
            return hasnext;
        }

        @Override
        public byte[] next()
        {
            try
            {
                byte data[] = new byte[2048];
                int count;
                while ((count = origin.read(data, 0, data.length)) != -1)
                {
                    compress.write(data, 0, count);
                    if (bos.size() >= chunkSize)
                        return returnSafe();
                }
                // We don't have anything else to read hence set to false.
                return done();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        private byte[] done() throws IOException
        {
            compress.flush();
            byte[] return_ = bos.toByteArray();
            hasnext = false;
            IOUtils.closeQuietly(compress);
            IOUtils.closeQuietly(bos);
            try
            {
                if (origin != null)
                    origin.close();
            }
            catch (IOException ex)
            {
                // do nothing.
            }
            return return_;
        }

        private byte[] returnSafe() throws IOException
        {
            byte[] return_ = bos.toByteArray();
            bos.reset();
            return return_;
        }

        @Override
        public void remove()
        {
        }
    }

    public class ChunkedRAFStream implements Iterator<byte[]>
    {
        private boolean hasnext = true;
        private ByteArrayOutputStream bos;
        private SnappyOutputStream compress;
        private RandomAccessFile origin;
        private long chunkSize;

        public ChunkedRAFStream(RandomAccessFile fis, long chunkSize) throws IOException
        {
            this.origin = fis;
            this.bos = new ByteArrayOutputStream();
            this.compress = new SnappyOutputStream(bos);
            // Aggressive
            if (fis.length() / chunkSize >= MAX_CHUNKS)
                this.chunkSize = fis.length() / (MAX_CHUNKS - 1);
            else
                this.chunkSize = chunkSize;
        }

        @Override
        public boolean hasNext()
        {
            return hasnext;
        }

        @Override
        public byte[] next()
        {
            try
            {
                byte data[] = new byte[2048];
                int count;
                while ((count = origin.read(data, 0, data.length)) != -1)
                {
                    compress.write(data, 0, count);
                    if (bos.size() >= chunkSize)
                        return returnSafe();
                }
                // We don't have anything else to read hence set to false.
                return done();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        private byte[] done() throws IOException
        {
            compress.flush();
            byte[] return_ = bos.toByteArray();
            hasnext = false;
            IOUtils.closeQuietly(compress);
            IOUtils.closeQuietly(bos);
            try
            {
                if (origin != null)
                    origin.close();
            }
            catch (IOException ex)
            {
                // do nothing.
            }
            return return_;
        }

        private byte[] returnSafe() throws IOException
        {
            byte[] return_ = bos.toByteArray();
            bos.reset();
            return return_;
        }

        @Override
        public void remove()
        {
        }
    }

}

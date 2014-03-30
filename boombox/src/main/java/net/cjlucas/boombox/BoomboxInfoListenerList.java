package net.cjlucas.boombox;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

public class BoomboxInfoListenerList implements Iterable<BoomboxInfoListener> {
    private ArrayList<WeakReference<BoomboxInfoListener>> mListeners =
            new ArrayList<WeakReference<BoomboxInfoListener>>();

    private class BoomboxInfoListenerIterator implements Iterator<BoomboxInfoListener> {
        private ArrayList<WeakReference<BoomboxInfoListener>> mArrayList;
        private int mCursor;

        public BoomboxInfoListenerIterator(ArrayList<WeakReference<BoomboxInfoListener>> list) {
            mArrayList = list;
        }

        private void shiftCursor() {
            for (int i = mCursor; i < mArrayList.size(); i++) {
                if (mArrayList.get(i).get() == null) mCursor++;
            }
        }

        @Override
        public boolean hasNext() {
            shiftCursor();
            return mCursor < mArrayList.size();
        }

        @Override
        public BoomboxInfoListener next() {
            shiftCursor();
            return mArrayList.get(mCursor++).get();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public synchronized void add(BoomboxInfoListener infoListener) {
        mListeners.add(new WeakReference<BoomboxInfoListener>(infoListener));
    }

    public synchronized void remove(BoomboxInfoListener infoListener) {
        purgeNullReferences();
        for (int i = 0; i < mListeners.size(); i++) {
            if (mListeners.get(i).get() == infoListener) {
                mListeners.remove(i);
                break;
            }
        }
    }

    private void purgeNullReferences() {
        for (int i = 0; i < mListeners.size();) {
            if (mListeners.get(i).get() == null) {
                mListeners.remove(i);
            } else {
                i++;
            }
        }
    }

    @Override
    public synchronized Iterator<BoomboxInfoListener> iterator() {
        purgeNullReferences();
        return new BoomboxInfoListenerIterator(
                new ArrayList<WeakReference<BoomboxInfoListener>>(mListeners));
    }
}

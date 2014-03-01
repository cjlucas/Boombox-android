package net.cjlucas.boombox;

import net.cjlucas.boombox.provider.AudioDataProvider;

public interface BoomboxInfoListener
{
    void onPlaybackStart(Boombox boombox, AudioDataProvider provider);

    void onPlaybackCompletion(Boombox           boombox,
                              AudioDataProvider completedProvider,
                              AudioDataProvider nextProvider);

    void onPlaylistCompletion(Boombox boombox);

    void onBufferingStart(Boombox boombox, AudioDataProvider provider);

    void onBufferingEnd(Boombox boombox, AudioDataProvider provider);

    void onBufferingUpdate(Boombox           boombox,
                           AudioDataProvider provider,
                           int               percentComplete);
}

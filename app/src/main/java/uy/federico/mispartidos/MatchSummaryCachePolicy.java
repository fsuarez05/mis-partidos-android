package uy.federico.mispartidos;

final class MatchSummaryCachePolicy {
    private MatchSummaryCachePolicy(){}

    static boolean shouldAccept(boolean useful,boolean previousUseful,boolean previousRecent,
                                boolean fallback,boolean previousFallback,int score,int previousScore,
                                int sources,int previousSources,boolean transmissionConfirmed,
                                boolean previousTransmissionConfirmed){
        if(!useful)return false;
        if(!previousUseful)return true;
        if(transmissionConfirmed&&!previousTransmissionConfirmed)return true;
        if(previousRecent&&fallback&&!previousFallback)return false;
        return !previousRecent||score>previousScore||(score==previousScore&&sources>=previousSources);
    }
}

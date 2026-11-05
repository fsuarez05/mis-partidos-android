package uy.federico.mispartidos;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class RecentResultsPolicyTest {
    private static final long NOW=1_800_000_000_000L;
    private static final long DAY=24L*60L*60L*1000L;
    private Match result(long id,String home,String away,long ageDays,int h,int a,String status,String fixture) {
        return new Match(id,home,away,"Liga Profesional",NOW-ageDays*DAY,false,
                h,a,fixture,home,away,"","","Uruguay",status);
    }
    private java.util.function.Function<Match,Set<String>> selected(String... favorites){
        Set<String> selected=new LinkedHashSet<>(Arrays.asList(favorites));
        return m->{
            Set<String> names=new LinkedHashSet<>();
            if(selected.contains(m.local()))names.add(m.local());
            if(selected.contains(m.visitante()))names.add(m.visitante());
            return names;
        };
    }
    @Test public void oneLastMatchPerFavorite(){
        Match recent=result(2,"Peñarol","Danubio",2,1,0,FixtureStatusPolicy.FINISHED,"a");
        Match older=result(1,"Peñarol","Defensor",8,3,0,FixtureStatusPolicy.FINISHED,"b");
        List<Match> out=RecentResultsPolicy.latestPerFavorite(Arrays.asList(older,recent),selected("Peñarol"),NOW);
        assertEquals(1,out.size());assertEquals("a",out.get(0).fixtureId);
    }
    @Test public void lastResultForEachFavoriteNotGlobalLast(){
        Match p=result(2,"Peñarol","Danubio",2,1,0,FixtureStatusPolicy.FINISHED,"a");
        Match l=result(3,"Liverpool","Chelsea",4,1,1,FixtureStatusPolicy.FINISHED,"b");
        List<Match> out=RecentResultsPolicy.latestPerFavorite(Arrays.asList(l,p),selected("Peñarol","Liverpool","Chelsea"),NOW);
        assertEquals(2,out.size());assertEquals("a",out.get(0).fixtureId);assertEquals("b",out.get(1).fixtureId);
    }
    @Test public void matchBetweenTwoFavoritesAppearsOnce(){
        Match derby=result(1,"Peñarol","Nacional",2,2,1,FixtureStatusPolicy.FINISHED,"derby");
        List<Match> out=RecentResultsPolicy.latestPerFavorite(Collections.singletonList(derby),selected("Peñarol","Nacional"),NOW);
        assertEquals(1,out.size());
    }
    @Test public void newestSharedMatchFillsBothFavorites(){
        Match derby=result(1,"Peñarol","Nacional",1,2,1,FixtureStatusPolicy.FINISHED,"derby");
        Match p=result(2,"Peñarol","Danubio",2,1,0,FixtureStatusPolicy.FINISHED,"p");
        Match n=result(3,"Nacional","Defensor",3,2,1,FixtureStatusPolicy.FINISHED,"n");
        assertEquals(1,RecentResultsPolicy.latestPerFavorite(Arrays.asList(p,n,derby),selected("Peñarol","Nacional"),NOW).size());
    }
    @Test public void olderSharedMatchStillRepresentsFavoriteWithoutNewerMatch(){
        Match latest=result(1,"Peñarol","Danubio",1,2,1,FixtureStatusPolicy.FINISHED,"latest");
        Match older=result(2,"Peñarol","Nacional",4,0,1,FixtureStatusPolicy.FINISHED,"derby");
        List<Match> out=RecentResultsPolicy.latestPerFavorite(Arrays.asList(older,latest),selected("Peñarol","Nacional"),NOW);
        assertEquals(2,out.size());
    }
    @Test public void completedWithoutScoreDoesNotReplaceKnownValidResult(){
        Match unknown=result(2,"Peñarol","Danubio",1,-1,-1,FixtureStatusPolicy.FINISHED,"new");
        Match saved=result(1,"Peñarol","Nacional",5,2,0,FixtureStatusPolicy.FINISHED,"old");
        List<Match> out=RecentResultsPolicy.latestPerFavorite(Arrays.asList(saved,unknown),selected("Peñarol"),NOW);
        assertEquals(1,out.size());assertEquals("old",out.get(0).fixtureId);
    }
    @Test public void previousScoreReplacedByNewerVerifiedResult(){
        Match saved=result(1,"Peñarol","Nacional",5,2,0,FixtureStatusPolicy.FINISHED,"old");
        Match newer=result(2,"Peñarol","Danubio",1,1,0,FixtureStatusPolicy.FINISHED,"new");
        List<Match> out=RecentResultsPolicy.latestPerFavorite(Arrays.asList(saved,newer),selected("Peñarol"),NOW);
        assertEquals("new",out.get(0).fixtureId);
    }
    @Test public void zeroZeroIsValid(){
        Match draw=result(1,"Peñarol","Danubio",1,0,0,FixtureStatusPolicy.FINISHED,"draw");
        assertEquals(1,RecentResultsPolicy.latestPerFavorite(Collections.singletonList(draw),selected("Peñarol"),NOW).size());
    }
    @Test public void olderThan30DaysExcluded(){
        Match old=result(1,"Peñarol","Nacional",31,2,1,FixtureStatusPolicy.FINISHED,"old");
        assertTrue(RecentResultsPolicy.latestPerFavorite(Collections.singletonList(old),selected("Peñarol"),NOW).isEmpty());
    }
    @Test public void lastMatchWithin30DaysKeptEvenIfAWeekOld(){
        Match last=result(1,"Peñarol","Nacional",9,1,1,FixtureStatusPolicy.FINISHED,"valid");
        assertEquals(1,RecentResultsPolicy.latestPerFavorite(Collections.singletonList(last),selected("Peñarol"),NOW).size());
    }
    @Test public void futureMatchesExcluded(){
        Match future=result(1,"Peñarol","Danubio",-1,2,1,FixtureStatusPolicy.SCHEDULED,"future");
        assertTrue(RecentResultsPolicy.latestPerFavorite(Collections.singletonList(future),selected("Peñarol"),NOW).isEmpty());
    }
    @Test public void liveScoreNotMisclassifiedAsFinished(){
        Match live=new Match(1,"Peñarol","Danubio","Liga",NOW-60_000L,false,
                1,0,"live","Peñarol","Danubio","","","Uruguay",FixtureStatusPolicy.LIVE);
        assertTrue(RecentResultsPolicy.latestPerFavorite(Collections.singletonList(live),selected("Peñarol"),NOW).isEmpty());
    }
    @Test public void unrelatedTeamsExcluded(){
        Match match=result(1,"Danubio","Defensor",2,1,0,FixtureStatusPolicy.FINISHED,"x");
        assertTrue(RecentResultsPolicy.latestPerFavorite(Collections.singletonList(match),selected("Peñarol"),NOW).isEmpty());
    }
    @Test public void duplicateFixtureIdAppearsOnce(){
        Match a=result(1,"Peñarol","Nacional",2,1,0,FixtureStatusPolicy.FINISHED,"same");
        Match b=result(2,"Peñarol","Nacional",2,1,0,FixtureStatusPolicy.FINISHED,"same");
        assertEquals(1,RecentResultsPolicy.latestPerFavorite(Arrays.asList(a,b),selected("Peñarol","Nacional"),NOW).size());
    }
    @Test public void emptyInputSafe(){
        assertTrue(RecentResultsPolicy.latestPerFavorite(Collections.emptyList(),selected("Peñarol"),NOW).isEmpty());
    }
    @Test public void selectedFavoriteRemovedFromList(){
        Match a=result(1,"Peñarol","Danubio",1,1,0,FixtureStatusPolicy.FINISHED,"a");
        assertTrue(RecentResultsPolicy.latestPerFavorite(Collections.singletonList(a),selected("Chelsea"),NOW).isEmpty());
    }
}

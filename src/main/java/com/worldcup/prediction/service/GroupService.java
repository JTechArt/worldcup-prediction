package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.dto.GroupStandingDto;
import java.util.List;
import java.util.Map;

public interface GroupService {

    Map<String, List<GroupStandingDto>> getAllGroupStandings();

    List<String> getQualifiedThirdPlaceGroups();

    Map<String, List<Match>> getMatchesByGroup();
}

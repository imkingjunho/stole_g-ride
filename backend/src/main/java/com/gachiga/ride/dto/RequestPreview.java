package com.gachiga.ride.dto;

import com.gachiga.contract.route.RouteResult;
import java.util.List;

/** 요청을 생성하기 전에 해당 출발지의 인원 선택지와 단독 이동 경로를 보여 준다. */
public record RequestPreview(List<Integer> groupSizes, List<RouteResult.Section> sections, boolean estimated) {}

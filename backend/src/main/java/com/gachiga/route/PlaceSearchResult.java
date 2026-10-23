package com.gachiga.route;

/** 카카오 로컬 키워드 검색 결과 중 프론트에 공개할 필드. */
public record PlaceSearchResult(String name, String address, double lat, double lng) {}

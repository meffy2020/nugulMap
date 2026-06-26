package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.dto.HotplaceResponse;
import com.neogulmap.neogul_map.dto.InsightStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HotplaceServiceTest {

    private static final String JAMSIL_TOURISM_CITYDATA_URL = "http://openapi.seoul.go.kr:8088/test-key/xml/citydata/1/5/POI005";
    private static final String JAMSIL_SAENAE_CITYDATA_URL = "http://openapi.seoul.go.kr:8088/test-key/xml/citydata/1/5/POI118";
    private static final String SONGRIDAN_CITYDATA_URL = "http://openapi.seoul.go.kr:8088/test-key/xml/citydata/1/5/%EC%86%A1%EB%A6%AC%EB%8B%A8%EA%B8%B8%C2%B7%ED%98%B8%EC%88%98%EB%8B%A8%EA%B8%B8";

    @Test
    void getHotplacesReturnsLotteWorldFallbackWithoutExternalKey() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces("롯데", 3);

        assertThat(response.dataFreshness()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.places()).isNotEmpty();
        assertThat(response.places().getFirst().name()).contains("롯데월드");
        assertThat(response.places().getFirst().source()).isEqualTo("STATIC_SEED");
        assertThat(response.sources())
                .contains("서울특별시_실시간 도시데이터(통신 기지국 기반 실시간 인구 추정)");
    }

    @Test
    void getHotplacesCapsLimitAndKeepsSeededHotplaceCandidates() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces(null, 50);

        assertThat(response.places()).hasSizeLessThanOrEqualTo(20);
        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::name)
                .contains("롯데월드·잠실", "성수동 카페거리", "강남역", "명동");
    }

    @Test
    void getHotplacesSearchesExpandedSeoulRealtimeCityDataCandidates() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse gangnam = service.getHotplaces("강남", 10);
        HotplaceResponse seongsu = service.getHotplaces("성수", 10);

        assertThat(gangnam.places())
                .extracting(HotplaceResponse.HotplaceItem::name)
                .contains("강남역", "코엑스·강남 MICE");
        assertThat(seongsu.places())
                .extracting(HotplaceResponse.HotplaceItem::name)
                .contains("성수동 카페거리");
    }

    @Test
    void getHotplacesTreatsHotNowAsCitywideRankedShortcut() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces("hot-now", 5);

        assertThat(response.dataFreshness()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.places()).hasSize(5);
        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::name)
                .contains("홍대입구", "성수동 카페거리", "강남역", "롯데월드·잠실");
    }

    @Test
    void getHotplacesTreatsKoreanHotNowAsCitywideRankedShortcut() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces("지금 핫한 곳", 5);

        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::id)
                .contains("hongdae", "seongsu", "gangnam-station");
    }

    @Test
    void getHotplacesUnderstandsNaturalCrowdQuestionForLotteWorld() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces("롯데월드 사람 많아?", 3);

        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::id)
                .containsExactly("lotte-world");
    }

    @Test
    void getHotplacesKeepsSpecificPlaceWhenHotplaceWordIsIncluded() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces("성수 핫플", 5);

        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::id)
                .containsExactly("seongsu");
    }

    @Test
    void getHotplacesTreatsCitywideHotplaceQuestionAsHotNow() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces("서울 핫플 어디야", 4);

        assertThat(response.places()).hasSize(4);
        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::id)
                .contains("hongdae", "seongsu", "gangnam-station");
    }

    @Test
    void getHotplacesFiltersCandidatesByViewportBounds() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");

        HotplaceResponse response = service.getHotplaces(null, 10, 37.53, 37.56, 127.04, 127.07);

        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::name)
                .containsExactly("성수동 카페거리");
    }

    @Test
    void getHotplacesParsesSeoulCityDataCrowdEstimateForLotteWorld() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "test-key");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(JAMSIL_TOURISM_CITYDATA_URL))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <CITYDATA>
                            <AREA_NM>잠실 관광특구</AREA_NM>
                            <LIVE_PPLTN_STTS>
                              <LIVE_PPLTN_STTS>
                                <AREA_CONGEST_LVL>약간 붐빔</AREA_CONGEST_LVL>
                                <AREA_CONGEST_MSG>사람이 다소 많은 편입니다.</AREA_CONGEST_MSG>
                                <AREA_PPLTN_MIN>12000</AREA_PPLTN_MIN>
                                <AREA_PPLTN_MAX>14000</AREA_PPLTN_MAX>
                                <PPLTN_TIME>2026-06-18 20:00</PPLTN_TIME>
                              </LIVE_PPLTN_STTS>
                            </LIVE_PPLTN_STTS>
                          </CITYDATA>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.places()).hasSize(1);
        HotplaceResponse.HotplaceItem lotteWorld = response.places().getFirst();
        assertThat(lotteWorld.name()).isEqualTo("롯데월드·잠실");
        assertThat(lotteWorld.crowdLevel()).isEqualTo("약간 붐빔");
        assertThat(lotteWorld.estimatedMinPeople()).isEqualTo(12000);
        assertThat(lotteWorld.estimatedMaxPeople()).isEqualTo(14000);
        assertThat(lotteWorld.source()).isEqualTo("SEOUL_CITYDATA");
        assertThat(lotteWorld.sourcePlaceCode()).isEqualTo("잠실 관광특구");
    }

    @Test
    void getHotplacesFallsBackWhenSeoulCityDataHasNoCrowdSignal() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "test-key");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(JAMSIL_TOURISM_CITYDATA_URL))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <CITYDATA>
                            <AREA_NM>잠실 관광특구</AREA_NM>
                          </CITYDATA>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.places()).hasSize(1);
        assertThat(response.places().getFirst().source()).isEqualTo("STATIC_SEED");
        assertThat(response.places().getFirst().sourcePlaceCode()).isEqualTo("잠실 관광특구");
    }

    @Test
    void getHotplacesTriesJamsilCityDataAliasesWhenLegacyPlaceCodeIsInvalid() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "test-key");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(JAMSIL_TOURISM_CITYDATA_URL))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <RESULT>
                            <CODE>INFO-200</CODE>
                            <MESSAGE>해당하는 데이터가 없습니다.</MESSAGE>
                          </RESULT>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));
        server.expect(requestTo("http://openapi.seoul.go.kr:8088/test-key/xml/citydata/1/5/%EC%9E%A0%EC%8B%A4%20%EA%B4%80%EA%B4%91%ED%8A%B9%EA%B5%AC"))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <RESULT>
                            <CODE>INFO-200</CODE>
                            <MESSAGE>해당하는 데이터가 없습니다.</MESSAGE>
                          </RESULT>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));
        server.expect(requestTo(JAMSIL_SAENAE_CITYDATA_URL))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <CITYDATA>
                            <AREA_NM>잠실새내역</AREA_NM>
                            <LIVE_PPLTN_STTS>
                              <LIVE_PPLTN_STTS>
                                <AREA_CONGEST_LVL>붐빔</AREA_CONGEST_LVL>
                                <AREA_CONGEST_MSG>주변 유동인구가 많습니다.</AREA_CONGEST_MSG>
                                <AREA_PPLTN_MIN>19000</AREA_PPLTN_MIN>
                                <AREA_PPLTN_MAX>23000</AREA_PPLTN_MAX>
                                <PPLTN_TIME>2026-06-18 22:00</PPLTN_TIME>
                              </LIVE_PPLTN_STTS>
                            </LIVE_PPLTN_STTS>
                          </CITYDATA>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        HotplaceResponse.HotplaceItem item = response.places().getFirst();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(item.source()).isEqualTo("SEOUL_CITYDATA");
        assertThat(item.sourcePlaceCode()).isEqualTo("잠실새내역");
        assertThat(item.estimatedMinPeople()).isEqualTo(19000);
        assertThat(item.estimatedMaxPeople()).isEqualTo(23000);
    }

    @Test
    void getHotplacesCachesSeoulCityDataWithinTtl() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "test-key");
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 180L);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(JAMSIL_TOURISM_CITYDATA_URL))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <CITYDATA>
                            <AREA_NM>잠실 관광특구</AREA_NM>
                            <LIVE_PPLTN_STTS>
                              <LIVE_PPLTN_STTS>
                                <AREA_CONGEST_LVL>붐빔</AREA_CONGEST_LVL>
                                <AREA_CONGEST_MSG>사람이 많습니다.</AREA_CONGEST_MSG>
                                <AREA_PPLTN_MIN>18000</AREA_PPLTN_MIN>
                                <AREA_PPLTN_MAX>22000</AREA_PPLTN_MAX>
                                <PPLTN_TIME>2026-06-18 21:00</PPLTN_TIME>
                              </LIVE_PPLTN_STTS>
                            </LIVE_PPLTN_STTS>
                          </CITYDATA>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));

        HotplaceResponse first = service.getHotplaces("롯데월드", 1);
        HotplaceResponse second = service.getHotplaces("롯데월드", 1);

        server.verify();
        assertThat(first.places().getFirst().estimatedMaxPeople()).isEqualTo(22000);
        assertThat(second.places().getFirst().estimatedMaxPeople()).isEqualTo(22000);
        assertThat(second.places().getFirst().source()).isEqualTo("SEOUL_CITYDATA");
    }

    @Test
    void getHotplacesFallsBackToSeoulAreaNameWhenCityDataCodeIsMissing() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "test-key");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SONGRIDAN_CITYDATA_URL))
                .andRespond(withSuccess("""
                        <SeoulRtd.citydata>
                          <CITYDATA>
                            <AREA_NM>송리단길·호수단길</AREA_NM>
                          </CITYDATA>
                        </SeoulRtd.citydata>
                        """, new org.springframework.http.MediaType("application", "xml", StandardCharsets.UTF_8)));

        HotplaceResponse response = service.getHotplaces("송리단길", 1);

        server.verify();
        assertThat(response.places()).hasSize(1);
        assertThat(response.places().getFirst().source()).isEqualTo("STATIC_SEED");
        assertThat(response.places().getFirst().sourcePlaceCode()).isEqualTo("송리단길·호수단길");
    }

    @Test
    void getHotplacesUsesConfiguredTelecomCrowdAdapterBeforeSeoulCityData() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?place={placeId}");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKeyHeader", "appKey");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "seoul-key");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://telecom.example/crowd?place=lotte-world"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "crowdLevel": "붐빔",
                            "crowdMessage": "통신사 기준 방문자가 많은 편입니다.",
                            "estimatedMinPeople": 21000,
                            "estimatedMaxPeople": 25000,
                            "sourcePlaceCode": "sk-lotte-world",
                            "updatedAt": "2026-06-18T12:00:00Z"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        HotplaceResponse.HotplaceItem item = response.places().getFirst();
        assertThat(item.source()).isEqualTo("TELECOM_CROWD");
        assertThat(item.crowdLevel()).isEqualTo("붐빔");
        assertThat(item.estimatedMinPeople()).isEqualTo(21000);
        assertThat(item.estimatedMaxPeople()).isEqualTo(25000);
        assertThat(item.sourcePlaceCode()).isEqualTo("sk-lotte-world");
        InsightStatusResponse.ProviderStatus status = service.getTelecomCrowdProviderStatus();
        assertThat(status.qualityStatus()).isEqualTo("OK");
    }

    @Test
    void getHotplacesSupportsTelecomApiKeyInUrlTemplateWithoutHeader() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?key={apiKey}&area={seoulAreaCode}");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKeyHeader", "none");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://telecom.example/crowd?key=telecom+key&area=POI005"))
                .andExpect(request -> assertThat(request.getHeaders().containsKey("appKey")).isFalse())
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "congestionLevel": "붐빔",
                            "populationMin": 22000,
                            "populationMax": 27000,
                            "sourcePlaceCode": "query-key-lotte-world"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        HotplaceResponse.HotplaceItem item = response.places().getFirst();
        assertThat(item.source()).isEqualTo("TELECOM_CROWD");
        assertThat(item.estimatedMinPeople()).isEqualTo(22000);
        assertThat(item.estimatedMaxPeople()).isEqualTo(27000);
        assertThat(item.sourcePlaceCode()).isEqualTo("query-key-lotte-world");
    }

    @Test
    void getHotplacesParsesNestedTelecomCrowdItemResponse() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?place={placeId}");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKeyHeader", "appKey");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://telecom.example/crowd?place=lotte-world"))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "body": {
                              "items": [
                                {
                                  "congestionLevel": "약간 붐빔",
                                  "congestionMessage": "방문자가 늘고 있습니다.",
                                  "populationMin": "15000",
                                  "populationMax": "18000",
                                  "poiId": "carrier-lotte-world",
                                  "baseTime": "2026-06-18T13:00:00Z"
                                }
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        HotplaceResponse.HotplaceItem item = response.places().getFirst();
        assertThat(item.source()).isEqualTo("TELECOM_CROWD");
        assertThat(item.crowdLevel()).isEqualTo("약간 붐빔");
        assertThat(item.estimatedMinPeople()).isEqualTo(15000);
        assertThat(item.estimatedMaxPeople()).isEqualTo(18000);
        assertThat(item.sourcePlaceCode()).isEqualTo("carrier-lotte-world");
        assertThat(item.updatedAt()).isEqualTo("2026-06-18T13:00:00Z");
    }

    @Test
    void getHotplacesParsesGeoJsonTelecomCrowdProperties() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?name={placeName}");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKeyHeader", "appKey");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://telecom.example/crowd?name=%EB%A1%AF%EB%8D%B0%EC%9B%94%EB%93%9C%C2%B7%EC%9E%A0%EC%8B%A4"))
                .andRespond(withSuccess("""
                        {
                          "features": [
                            {
                              "type": "Feature",
                              "properties": {
                                "level": "보통",
                                "message": "평소 수준입니다.",
                                "minPeople": 9000,
                                "maxPeople": 12000,
                                "id": "geojson-lotte-world",
                                "datetime": "2026-06-18T14:00:00Z"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        HotplaceResponse.HotplaceItem item = response.places().getFirst();
        assertThat(item.source()).isEqualTo("TELECOM_CROWD");
        assertThat(item.crowdLevel()).isEqualTo("보통");
        assertThat(item.estimatedMinPeople()).isEqualTo(9000);
        assertThat(item.estimatedMaxPeople()).isEqualTo(12000);
        assertThat(item.sourcePlaceCode()).isEqualTo("geojson-lotte-world");
    }

    @Test
    void getHotplacesNormalizesTelecomCrowdCodesAndPopulationRanges() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?place={placeId}");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKeyHeader", "appKey");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://telecom.example/crowd?place=lotte-world"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "congestionLevel": "VERY_CROWDED",
                            "populationRange": "12,000 - 18,500명",
                            "message": "carrier crowd range"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HotplaceResponse response = service.getHotplaces("롯데월드", 1);

        server.verify();
        HotplaceResponse.HotplaceItem item = response.places().getFirst();
        assertThat(item.source()).isEqualTo("TELECOM_CROWD");
        assertThat(item.crowdLevel()).isEqualTo("붐빔");
        assertThat(item.estimatedMinPeople()).isEqualTo(12000);
        assertThat(item.estimatedMaxPeople()).isEqualTo(18500);
    }

    @Test
    void getHotplacesRanksSameCrowdLevelByEstimatedPeopleBeforeStaticHotRank() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?place={placeId}");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKeyHeader", "appKey");
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://telecom.example/crowd?place=lotte-world"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "congestionLevel": "HIGH",
                            "populationRange": "8,000~10,000명"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://telecom.example/crowd?place=lotte-tower-lake"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "congestionLevel": "HIGH",
                            "populationRange": "22,000~28,000명"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HotplaceResponse response = service.getHotplaces("롯데", 2);

        server.verify();
        assertThat(response.dataFreshness()).isEqualTo("LIVE_OR_PARTIAL");
        assertThat(response.places())
                .extracting(HotplaceResponse.HotplaceItem::id)
                .containsExactly("lotte-tower-lake", "lotte-world");
        assertThat(response.places().getFirst().estimatedMaxPeople()).isEqualTo(28000);
    }

    @Test
    void telecomCrowdStatusRequiresBothKeyAndUrlTemplate() {
        HotplaceService service = new HotplaceService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");

        InsightStatusResponse.ProviderStatus status = service.getTelecomCrowdProviderStatus();

        assertThat(status.qualityStatus()).isEqualTo("CONFIGURED_UNVERIFIED");
        assertThat(status.detail()).contains("TELECOM_CROWD_URL_TEMPLATE");
    }
}

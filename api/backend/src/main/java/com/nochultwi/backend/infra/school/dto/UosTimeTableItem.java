package com.nochultwi.backend.infra.school.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UosTimeTableItem(
        @JsonProperty("SUBJECT_NO") @JsonAlias("subject_no") String subjectNo,
        @JsonProperty("SUBJECT_NM") @JsonAlias("subject_nm") String subjectNm,
        @JsonProperty("DVCL_NO") @JsonAlias("dvcl_no") String dvclNo,
        @JsonProperty("CLASS_NM") @JsonAlias("class_nm") String classNm,
        @JsonProperty("PROF_KOR_NM") @JsonAlias("prof_kor_nm") String profKorNm,
        @JsonProperty("SUB_DEPT") @JsonAlias("sub_dept") String subDept,
        @JsonProperty("CREDIT") @JsonAlias("credit") Object credit,
        @JsonProperty("YEAR") @JsonAlias("year") String year,
        @JsonProperty("TERM") @JsonAlias("term") String term,
        @JsonProperty("SHYR") @JsonAlias("shyr") Object shyr,
        @JsonProperty("SUBJECT_DIV") @JsonAlias("subject_div") String subjectDiv,
        @JsonProperty("DAY_NIGHT_NM") @JsonAlias("day_night_nm") String dayNightNm,
        @JsonProperty("CLASS_TYPE") @JsonAlias("class_type") String classType
) {
}

package ru.rdc.PrintTalon.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceDTO {
    private Long keyid;
    private String room;
    private String sCode;
    private String sText;
    private LocalDateTime dat;
    private String dirForm;
    private String doctor;
    private String dirOrg;

    // для внутренней логики, не выводится на UI
    private String agrType;
    private Long psKeyid;
    private String visitId;
}

package ru.rdc.PrintTalon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabTestResultDto {
    private String pnum;
    private String fio;
    private String bdate;
    private String address;
    private String ids;
    private String material;
    private String lpu;
    private String otd;
    private String executors;
    private String usl;
    private String labtest;
    private String value;
    private String units;
    private String norm;
    private String collecdate;
    private String finisdate;
    private String printdate;
    private int patstatus;
    private String testGroup;
    private int viewSortcode;
    private String tgSort;
    private String codeUsl;
    private String comments;
    private String device;
    private String vrachizmo;
    private String otdeleniemo;
    private String rdcLicense;
    private String rdcAddress;
    private String rdcHotline;
    private String rdcCallcenter;
    private String rdcSite;
    private String rdcEmail;
    private String rdcSprav;
    private String rdcMo;
}

//
/*
 * Copyright 2009-2014 PrimeTek.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pbouda.jeffrey.view.csv;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import java.time.LocalDate;
import java.util.Date;

@Named
@RequestScoped
public class ValidationView {
    
    private String text;
    private String description;
    private Integer integer;
    private Double doubleNumber;
    private Double money;
    private String regexText;
    private Date date;
    private Date date2;
    private Date date3;
    private LocalDate localDate;
    private LocalDate localDate2;
    private LocalDate localDate3;

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getInteger() {
        return integer;
    }
    public void setInteger(Integer integer) {
        this.integer = integer;
    }

    public Double getDoubleNumber() {
        return doubleNumber;
    }
    public void setDoubleNumber(Double doubleNumber) {
        this.doubleNumber = doubleNumber;
    }

    public Double getMoney() {
        return money;
    }
    public void setMoney(Double money) {
        this.money = money;
    }

    public String getRegexText() {
        return regexText;
    }
    public void setRegexText(String regexText) {
        this.regexText = regexText;
    }

    public Date getDate() {
        return date;
    }
    public void setDate(Date date) {
        this.date = date;
    }

    public Date getDate2() {
        return date2;
    }
    public void setDate2(Date date) {
        this.date2 = date;
    }

    public Date getDate3() {
        return date3;
    }
    public void setDate3(Date date) {
        this.date3 = date;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }
    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    public LocalDate getLocalDate2() {
        return localDate2;
    }
    public void setLocalDate2(LocalDate localDate) {
        this.localDate2 = localDate;
    }

    public LocalDate getLocalDate3() {
        return localDate3;
    }
    public void setLocalDate3(LocalDate localDate) {
        this.localDate3 = localDate;
    }
}

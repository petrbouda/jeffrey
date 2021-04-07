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
package pbouda.jeffrey.view.input;

import org.primefaces.PrimeFaces;
import org.primefaces.event.SelectEvent;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Named
@ViewScoped
public class CalendarJava8View implements Serializable {

    private Boolean timeInput = Boolean.FALSE;
    private Boolean showOtherMonths = Boolean.FALSE;

    private LocalDate date1;
    private LocalDate date2;
    private LocalDate date3;
    private LocalDate date4;
    private LocalDate date5;
    private LocalDate date6;
    private LocalDate date7;
    private LocalDate date8;
    private LocalDate date9;
    private LocalDate dateDe;
    private LocalDate date10;
    private LocalDate date11;
    private LocalDate date12;
    private LocalDate date13;
    private LocalTime time1;
    private LocalTime time2;
    private LocalTime time3;
    private LocalTime time4;
    private LocalTime time5;
    private LocalTime time6;
    private LocalTime time7;
    private LocalDateTime dateTime1;
    private LocalDateTime dateTime2;
    private LocalDateTime dateTime3;
    private LocalDateTime dateTime4;
    private LocalDateTime dateTime5;
    private LocalDateTime dateTime6;
    private LocalDateTime dateTimeDe;
    private List<LocalDate> multi;
    private List<LocalDate> range;
    private List<LocalDate> invalidDates;
    private List<Integer> invalidDays;
    private LocalDate minDate;
    private LocalDate maxDate;
    private LocalTime minTime;
    private LocalTime maxTime;
    private LocalDateTime minDateTime;
    private LocalDateTime maxDateTime;
    private ZonedDateTime zonedDateTime1;
    private YearMonth yearMonth;

    @PostConstruct
    public void init() {
        invalidDates = new ArrayList<>();
        invalidDates.add(LocalDate.now());
        for (int i = 0; i < 5; i++) {
            invalidDates.add(invalidDates.get(i).plusDays(1));
        }

        invalidDays = new ArrayList<>();
        invalidDays.add(0); /* the first day of week is disabled */
        invalidDays.add(3);

        minDate = LocalDate.now().minusYears(1);
        maxDate = LocalDate.now().plusYears(1);

        minTime = LocalTime.of(9, 0);
        maxTime = LocalTime.of(17, 0);

        minDateTime = LocalDateTime.now().minusWeeks(1);
        maxDateTime = LocalDateTime.now().plusWeeks(1);

        dateDe = LocalDate.of(2019, 7, 27);
        dateTimeDe = LocalDateTime.of(2019, 7, 27, 12, 59);
        dateTime4 = LocalDateTime.now();

        time4= LocalTime.of(10, 30);
    }

    public void onDateSelect(SelectEvent<LocalDate> event) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("dd/MM/yyyy");
        facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Date Selected", event.getObject().format(formatter)));
    }

    public void click() {
        PrimeFaces.current().ajax().update("form:display");
        PrimeFaces.current().executeScript("PF('dlg').show()");
    }

    public LocalDate getDate1() {
        return date1;
    }

    public void setDate1(LocalDate date1) {
        this.date1 = date1;
    }

    public LocalDate getDate2() {
        return date2;
    }

    public void setDate2(LocalDate date2) {
        this.date2 = date2;
    }

    public LocalDate getDate3() {
        return date3;
    }

    public void setDate3(LocalDate date3) {
        this.date3 = date3;
    }

    public LocalDate getDate4() {
        return date4;
    }

    public void setDate4(LocalDate date4) {
        this.date4 = date4;
    }

    public LocalDate getDate5() {
        return date5;
    }

    public void setDate5(LocalDate date5) {
        this.date5 = date5;
    }

    public LocalDate getDate6() {
        return date6;
    }

    public void setDate6(LocalDate date6) {
        this.date6 = date6;
    }

    public LocalDate getDate7() {
        return date7;
    }

    public void setDate7(LocalDate date7) {
        this.date7 = date7;
    }

    public LocalDate getDate8() {
        return date8;
    }

    public void setDate8(LocalDate date8) {
        this.date8 = date8;
    }

    public LocalDate getDate9() {
        return date9;
    }

    public void setDate9(LocalDate date9) {
        this.date9 = date9;
    }

    public LocalDate getDateDe() {
        return dateDe;
    }

    public void setDateDe(LocalDate dateDe) {
        this.dateDe = dateDe;
    }

    public LocalDate getDate10() {
        return date10;
    }

    public void setDate10(LocalDate date10) {
        this.date10 = date10;
    }

    public LocalDate getDate11() {
        return date11;
    }

    public void setDate11(LocalDate date11) {
        this.date11 = date11;
    }

    public LocalDate getDate12() {
        return date12;
    }

    public void setDate12(LocalDate date12) {
        this.date12 = date12;
    }

    public LocalDateTime getDateTime1() {
        return dateTime1;
    }

    public void setDateTime1(LocalDateTime dateTime1) {
        this.dateTime1 = dateTime1;
    }

    public LocalTime getTime1() {
        return time1;
    }

    public void setTime1(LocalTime time1) {
        this.time1 = time1;
    }

    public List<LocalDate> getMulti() {
        return multi;
    }

    public void setMulti(List<LocalDate> multi) {
        this.multi = multi;
    }

    public List<LocalDate> getRange() {
        return range;
    }

    public void setRange(List<LocalDate> range) {
        this.range = range;
    }

    public List<LocalDate> getInvalidDates() {
        return invalidDates;
    }

    public void setInvalidDates(List<LocalDate> invalidDates) {
        this.invalidDates = invalidDates;
    }

    public List<Integer> getInvalidDays() {
        return invalidDays;
    }

    public void setInvalidDays(List<Integer> invalidDays) {
        this.invalidDays = invalidDays;
    }

    public LocalDate getMinDate() {
        return minDate;
    }

    public void setMinDate(LocalDate minDate) {
        this.minDate = minDate;
    }

    public LocalDate getMaxDate() {
        return maxDate;
    }

    public void setMaxDate(LocalDate maxDate) {
        this.maxDate = maxDate;
    }

    public LocalDateTime getDateTimeDe() {
        return dateTimeDe;
    }

    public void setDateTimeDe(LocalDateTime dateTimeDe) {
        this.dateTimeDe = dateTimeDe;
    }

    public LocalTime getTime2() {
        return time2;
    }

    public void setTime2(LocalTime time2) {
        this.time2 = time2;
    }

    public LocalTime getTime3() {
        return time3;
    }

    public void setTime3(LocalTime time3) {
        this.time3 = time3;
    }

    public LocalDate getDate13() {
        return date13;
    }

    public void setDate13(LocalDate date13) {
        this.date13 = date13;
    }

    public ZonedDateTime getZonedDateTime1() {
        return zonedDateTime1;
    }

    public void setZonedDateTime1(ZonedDateTime zonedDateTime1) {
        this.zonedDateTime1 = zonedDateTime1;
    }

    public LocalDateTime getDateTime2() {
        return dateTime2;
    }

    public void setDateTime2(LocalDateTime dateTime2) {
        this.dateTime2 = dateTime2;
    }

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }

    public LocalTime getMinTime() {
        return minTime;
    }

    public void setMinTime(LocalTime minTime) {
        this.minTime = minTime;
    }

    public LocalTime getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(LocalTime maxTime) {
        this.maxTime = maxTime;
    }

    public LocalDateTime getMinDateTime() {
        return minDateTime;
    }

    public void setMinDateTime(LocalDateTime minDateTime) {
        this.minDateTime = minDateTime;
    }

    public LocalDateTime getMaxDateTime() {
        return maxDateTime;
    }

    public void setMaxDateTime(LocalDateTime maxDateTime) {
        this.maxDateTime = maxDateTime;
    }

    public LocalDateTime getDateTime3() {
        return dateTime3;
    }

    public void setDateTime3(LocalDateTime dateTime3) {
        this.dateTime3 = dateTime3;
    }

    public LocalTime getTime4() {
        return time4;
    }

    public void setTime4(LocalTime time4) {
        this.time4 = time4;
    }

    public LocalTime getTime5() {
        return time5;
    }

    public void setTime5(LocalTime time5) {
        this.time5 = time5;
    }

    public LocalTime getTime6() {
        return time6;
    }

    public void setTime6(LocalTime time6) {
        this.time6 = time6;
    }

    public LocalDateTime getDateTime4() {
        return dateTime4;
    }

    public void setDateTime4(LocalDateTime dateTime4) {
        this.dateTime4 = dateTime4;
    }

    public LocalDateTime getDateTime5() {
        return dateTime5;
    }

    public void setDateTime5(LocalDateTime dateTime5) {
        this.dateTime5 = dateTime5;
    }

    public Boolean getTimeInput() {
        return timeInput;
    }

    public void setTimeInput(Boolean timeInput) {
        this.timeInput = timeInput;
    }

    public Boolean getShowOtherMonths() {
        return showOtherMonths;
    }

    public void setShowOtherMonths(Boolean showOtherMonths) {
        this.showOtherMonths = showOtherMonths;
    }

    public LocalDateTime getDateTime6() {
        return dateTime6;
    }

    public void setDateTime6(LocalDateTime dateTime6) {
        this.dateTime6 = dateTime6;
    }

    public LocalTime getTime7() {
        return time7;
    }

    public void setTime7(LocalTime time7) {
        this.time7 = time7;
    }
}

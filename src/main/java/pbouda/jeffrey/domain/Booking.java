/*
 * Copyright 2009-2019 PrimeTek.
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
package pbouda.jeffrey.domain;

import java.io.Serializable;

public class Booking implements Serializable {

    private Integer roomNumber;
    private RoomCategory category;
    private String phone;
    private String comment;

    public Booking() {
    }

    public Booking(Integer roomNumber, RoomCategory category, String phone, String comment) {
        this.roomNumber = roomNumber;
        this.category = category;
        this.phone = phone;
        this.comment = comment;
    }

    public Integer getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(Integer roomNumber) {
        this.roomNumber = roomNumber;
    }

    public RoomCategory getCategory() {
        return category;
    }

    public void setCategory(RoomCategory category) {
        this.category = category;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Booking booking = (Booking) o;

        if (roomNumber != null ? !roomNumber.equals(booking.roomNumber) : booking.roomNumber != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return roomNumber != null ? roomNumber.hashCode() : 0;
    }
}

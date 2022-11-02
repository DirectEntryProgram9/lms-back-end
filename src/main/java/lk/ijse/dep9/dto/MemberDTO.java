package lk.ijse.dep9.dto;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import java.io.Serializable;

@JsonbPropertyOrder({"id", "name", "address", "contact"})
public class MemberDTO implements Serializable {
    private String id;
    private String name;
    private String address;
    private String contact;

    public MemberDTO() {
    }

    public MemberDTO(String id, String name, String address, String contact) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.contact = contact;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return "MemberDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", contact='" + contact + '\'' +
                '}';
    }
}

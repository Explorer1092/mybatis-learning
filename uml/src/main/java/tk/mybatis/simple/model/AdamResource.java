package tk.mybatis.simple.model;

import java.io.Serializable;
import java.util.Date;

/**
 * @author miles
 */
public class AdamResource implements Serializable {
    private Integer id;

    private Integer type;

    private String name;

    private String chineseName;

    private Integer parentId;

    private String description;

    private Integer deleted;

    private Integer ownerId;

    private String ownerEmail;

    private String updatePerson;

    private Integer special;

    private Date createTime;

    private Date updateTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName == null ? null : chineseName.trim();
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail == null ? null : ownerEmail.trim();
    }

    public String getUpdatePerson() {
        return updatePerson;
    }

    public void setUpdatePerson(String updatePerson) {
        this.updatePerson = updatePerson == null ? null : updatePerson.trim();
    }

    public Integer getSpecial() {
        return special;
    }

    public void setSpecial(Integer special) {
        this.special = special;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "AdamResource{" +
                "id=" + id +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", chineseName='" + chineseName + '\'' +
                ", parentId=" + parentId +
                ", description='" + description + '\'' +
                ", deleted=" + deleted +
                ", ownerId=" + ownerId +
                ", ownerEmail='" + ownerEmail + '\'' +
                ", updatePerson='" + updatePerson + '\'' +
                ", special=" + special +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
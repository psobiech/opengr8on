package pl.psobiech.opengr8on.xml.omp.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.List;

public class TreeObjectNode {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    @JsonProperty("_id")
    private final Long otherId;

    private final String name;

    private final String type;

    private final Boolean selected;

    private final Boolean link;

    private final Boolean removedLink;

    @JsonProperty("isDirty")
    private final Boolean dirty;

    private final SpecificObject specificObject;

    private final Reference parent;

    private final List<TreeObjectNode> children;

    @JsonCreator
    public TreeObjectNode(
            Long id, Long otherId, String name, String type,
            Boolean selected, Boolean link, Boolean removedLink, Boolean dirty, SpecificObject specificObject,
            Reference parent,
            List<TreeObjectNode> children
    ) {
        this.id = id;
        this.otherId = otherId;
        this.name = name;
        this.type = type;
        this.selected = selected;
        this.link = link;
        this.removedLink = removedLink;
        this.dirty = dirty;
        this.specificObject = specificObject;

        this.parent = parent;

        this.children = children;
    }

    public TreeObjectType getType() {
        return TreeObjectType.fromString(getTypeAsString());
    }

    public String getTypeAsString() {
        return type;
    }

    //

    public Long getId() {
        return id;
    }

    public Long getOtherId() {
        return otherId;
    }

    public String getName() {
        return name;
    }

    public boolean isSelected() {
        return Boolean.TRUE.equals(selected);
    }

    public boolean isLink() {
        return Boolean.TRUE.equals(link);
    }

    public boolean isRemovedLink() {
        return Boolean.TRUE.equals(removedLink);
    }

    public boolean isDirty() {
        return Boolean.TRUE.equals(dirty);
    }

    public SpecificObject getSpecificObject() {
        return specificObject;
    }

    public Long getParentId() {
        final Reference parent = getParent();
        if (parent == null) {
            return null;
        }

        return parent.getReference();
    }

    public Reference getParent() {
        return parent;
    }

    public List<TreeObjectNode> getChildren() {
        return Util.nullAsEmpty(children);
    }

    @Override
    public String toString() {
        return "TreeObject{" +
                "id=" + getId() +
                ", otherId=" + getOtherId() +
                ", name='" + getName() + '\'' +
                ", type='" + getTypeAsString() + "'/" + getType() +
                ", parentId=" + getParentId() +
                ", specificObject=" + getSpecificObject() +
                ", children.size()=" + getChildren().size() +
                '}';
    }

}

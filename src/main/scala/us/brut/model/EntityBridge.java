package us.brut.model;

import com.microsoft.azure.storage.table.TableServiceEntity;

/**
 * Needed to interface with Azure Entities,
 * as they don't recognize Scala case class parameters
 */
public class EntityBridge extends TableServiceEntity {
    public EntityBridge() { }

    private String Data;

    public String getData() {
        return this.Data;
    }

    public void setData (String Data) {
        this.Data = Data;
    }

}

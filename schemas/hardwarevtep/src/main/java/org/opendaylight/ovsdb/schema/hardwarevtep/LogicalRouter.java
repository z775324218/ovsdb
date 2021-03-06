/*
 * Copyright (c) 2014, 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ovsdb.schema.hardwarevtep;

import java.util.Map;
import org.opendaylight.ovsdb.lib.notation.Column;
import org.opendaylight.ovsdb.lib.notation.UUID;
import org.opendaylight.ovsdb.lib.schema.GenericTableSchema;
import org.opendaylight.ovsdb.lib.schema.typed.MethodType;
import org.opendaylight.ovsdb.lib.schema.typed.TypedBaseTable;
import org.opendaylight.ovsdb.lib.schema.typed.TypedColumn;
import org.opendaylight.ovsdb.lib.schema.typed.TypedTable;

@TypedTable(name = "Logical_Router", database = "hardware_vtep", fromVersion = "1.0.0")
public interface LogicalRouter extends TypedBaseTable<GenericTableSchema> {
    @TypedColumn(name = "name", method = MethodType.GETCOLUMN, fromVersion = "1.0.0")
    Column<GenericTableSchema, String> getNameColumn();

    @TypedColumn(name = "name", method = MethodType.GETDATA, fromVersion = "1.0.0")
    String getName();

    @TypedColumn(name = "name", method = MethodType.SETDATA, fromVersion = "1.0.0")
    void setName(String name);

    @TypedColumn(name = "description", method = MethodType.GETCOLUMN, fromVersion = "1.0.0")
    Column<GenericTableSchema, String> getDescriptionColumn();

    @TypedColumn(name = "description", method = MethodType.GETDATA, fromVersion = "1.0.0")
    String getDescription();

    @TypedColumn(name = "description", method = MethodType.SETDATA, fromVersion = "1.0.0")
    void setDescription(String description);

    @TypedColumn(name = "switch_binding", method = MethodType.GETCOLUMN, fromVersion = "1.0.0")
    Column<GenericTableSchema, Map<String, UUID>> getSwitchBindingColumn();

    @TypedColumn(name = "switch_binding", method = MethodType.SETDATA, fromVersion = "1.0.0")
    void setSwitchBinding(Map<String, UUID> switchBinding);

    @TypedColumn(name = "static_routes", method = MethodType.GETCOLUMN, fromVersion = "1.0.0")
    Column<GenericTableSchema, Map<String, String>> getStaticRoutesColumn();

    @TypedColumn(name = "static_routes", method = MethodType.SETDATA, fromVersion = "1.0.0")
    void setStaticRoutes(Map<String, String> staticRoutes);

    @TypedColumn(name = "acl_binding", method = MethodType.GETCOLUMN, fromVersion = "1.4.0")
    Column<GenericTableSchema, Map<String, UUID>> getAclBindingColumn();

    @TypedColumn(name = "acl_binding", method = MethodType.SETDATA, fromVersion = "1.4.0")
    void setAclBinding(Map<String, UUID> aclBinding);

    @TypedColumn(name = "other_config", method = MethodType.GETCOLUMN, fromVersion = "1.5.1")
    Column<GenericTableSchema, Map<String, String>> getOtherConfigColumn();

    @TypedColumn(name = "other_config", method = MethodType.SETDATA, fromVersion = "1.5.1")
    void setOtherConfig(Map<String, String> otherConfig);
}

create table broker_config.contexts
(
    id              char(36)                            not null
        primary key,
    name            varchar(255)                        not null,
    zoneid          char(36)                            not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    contextKey      varchar(255)                        null,
    constraint contextKey_UNIQUE
        unique (contextKey),
    constraint id_UNIQUE
        unique (id),
    constraint unique_zone_name
        unique (name, zoneid)
);

create index fk_context_zone_idx
    on broker_config.contexts (zoneid);

create index keyIdx
    on broker_config.contexts (contextKey);

create index nameIdx
    on broker_config.contexts (name);

create table broker_config.domains
(
    id              char(36)                            not null
        primary key,
    name            varchar(24)                         null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    domainKey       varchar(45)                         null,
    constraint domainKey_UNIQUE
        unique (domainKey),
    constraint unique_name
        unique (name)
);

create table broker_config.domainconfig
(
    id              bigint(21) unsigned auto_increment
        primary key,
    domainid        char(36)                            not null,
    `key`           varchar(128)                        not null,
    value           varchar(512)                        not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unique_domain_key
        unique (domainid, `key`),
    constraint fk_domain_domain_idx
        foreign key (domainid) references broker_config.domains (id)
            on delete cascade
);

create index keyIdx
    on broker_config.domains (domainKey);

create table broker_config.help
(
    id              mediumint auto_increment
        primary key,
    language_code   char(8)     default 'en'              not null,
    name            varchar(64) default '"TEST"'          not null,
    `key`           char(255)                             not null,
    type            char(64)    default 'article'         not null,
    article         varchar(512)                          null,
    template        varchar(256)                          null,
    lastUpdatedTime timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp   default CURRENT_TIMESTAMP not null,
    constraint unique_key
        unique (`key`)
);

create table broker_config.installation
(
    id                   char(36)                              not null
        primary key,
    masterkey            varchar(128)                          not null,
    lastUpdatedTime      timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime          timestamp   default CURRENT_TIMESTAMP not null,
    initializedFromRedis varchar(45) default 'false'           not null
);

create table broker_config.installationconfig
(
    id              bigint(21) unsigned auto_increment
        primary key,
    `key`           varchar(128)                        not null,
    value           varchar(512)                        not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unique_key
        unique (`key`)
);

create table broker_config.services
(
    id              char(36)                              not null
        primary key,
    servicename     varchar(128)                          not null,
    servicetype     varchar(36) default 'OBJECT'          not null,
    contextid       char(36)                              not null,
    lastUpdatedTime timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp   default CURRENT_TIMESTAMP not null,
    serviceKey      varchar(255)                          null,
    constraint serviceKey_UNIQUE
        unique (serviceKey),
    constraint unique_context_name_type
        unique (contextid, servicename, servicetype),
    constraint unique_context_service
        unique (servicename, servicetype, contextid),
    constraint fk_service_context
        foreign key (contextid) references broker_config.contexts (id)
            on delete cascade
);

create index fk_service_context_idx
    on broker_config.services (contextid);

create index keyIdx
    on broker_config.services (serviceKey);

create index nameIdx
    on broker_config.services (servicename);

create table broker_config.solutions
(
    id               char(36)                             not null
        primary key,
    name             varchar(45)                          null,
    domainid         char(36)                             not null,
    iscedarexpert    tinyint(1) default 0                 not null,
    forcessl         tinyint(1) default 0                 not null,
    failedloginlimit int        default 0                 not null,
    defaultzoneid    char(36)                             null,
    lastUpdatedTime  timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime      timestamp  default CURRENT_TIMESTAMP not null,
    solutionKey      varchar(128)                         null,
    enableDashboard  tinyint(1) default 0                 not null,
    dashboardIds     varchar(64)                          null,
    constraint id_UNIQUE
        unique (id),
    constraint solutionKey_UNIQUE
        unique (solutionKey),
    constraint unique_domain_name
        unique (domainid, name),
    constraint fk_solution_domain
        foreign key (domainid) references broker_config.domains (id)
            on delete cascade
);

create table broker_config.solutionServices
(
    id              char(36)                            not null
        primary key,
    serviceName     varchar(128)                        null,
    serviceType     varchar(45)                         null,
    solutionid      char(36)                            not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint unique_solution_name_type
        unique (solutionid, serviceName, serviceType),
    constraint fk_solutionServices_solution
        foreign key (solutionid) references broker_config.solutions (id)
            on delete cascade
);

create index fk_solutionServices_solution_idx
    on broker_config.solutionServices (solutionid);

create table broker_config.solutionconfig
(
    id              bigint(21) unsigned auto_increment
        primary key,
    solutionid      char(36)                            not null,
    `key`           varchar(128)                        not null,
    value           varchar(512)                        not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unique_solution_key
        unique (solutionid, `key`),
    constraint fk_solution_solution_idx
        foreign key (solutionid) references broker_config.solutions (id)
            on delete cascade
);

create index fk_solution_defaultzone_idx
    on broker_config.solutions (defaultzoneid);

create index fk_solution_domain_idx
    on broker_config.solutions (domainid);

create index keyIdx
    on broker_config.solutions (solutionKey);

create table broker_config.users
(
    id              char(36)                             not null
        primary key,
    domainid        char(36)                             not null,
    email           varchar(256)                         null,
    username        varchar(128)                         not null,
    password        varchar(128)                         not null,
    totpsecret      varchar(128)                         null,
    requiremfa      tinyint(1) default 0                 not null,
    locked          tinyint(1) default 0                 not null,
    lastUpdatedTime timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp  default CURRENT_TIMESTAMP not null,
    userKey         varchar(255)                         null,
    oldPassword     varchar(128)                         null,
    constraint id_UNIQUE
        unique (id),
    constraint unique_domain_name
        unique (domainid, username),
    constraint userKey_UNIQUE
        unique (userKey),
    constraint fk_user_domain
        foreign key (domainid) references broker_config.domains (id)
            on delete cascade
);

create table broker_config.userPreferences
(
    userid          char(36)                            not null,
    showHelp        bit                                 not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    id              char(36)                            not null
        primary key,
    constraint unique_user
        unique (userid),
    constraint fk_preferences_user
        foreign key (userid) references broker_config.users (id)
            on delete cascade
);

create index fk_preferences_user_idx
    on broker_config.userPreferences (userid);

create index fk_user_domain_idx
    on broker_config.users (domainid);

create index keyIdx
    on broker_config.users (userKey);

create table broker_config.zones
(
    id               char(36)                            not null
        primary key,
    name             varchar(255)                        not null,
    description      varchar(1024)                       not null,
    tags             varchar(255)                        null,
    solutionid       char(36)                            not null,
    lastUpdatedTime  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime      timestamp default CURRENT_TIMESTAMP not null,
    defaultcontextid char(36)                            null,
    zoneKey          varchar(255)                        null,
    constraint id_UNIQUE
        unique (id),
    constraint unq_name_solutionid
        unique (solutionid, name),
    constraint zoneKey_UNIQUE
        unique (zoneKey),
    constraint fk_zone_defaultcontext
        foreign key (defaultcontextid) references broker_config.contexts (id)
            on delete set null,
    constraint fk_zone_solution
        foreign key (solutionid) references broker_config.solutions (id)
            on delete cascade
);

create table broker_config.codesets
(
    id              char(36)                            not null
        primary key,
    name            varchar(255)                        not null,
    version         varchar(1024)                       not null,
    source          varchar(2048)                       null,
    zoneid          char(36)                            null,
    solutionid      char(36)                            null,
    domainid        char(36)                            null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unq_name_zoneid
        unique (zoneid, name),
    constraint fk_codeset_domain
        foreign key (domainid) references broker_config.domains (id)
            on delete cascade,
    constraint fk_codeset_solution
        foreign key (solutionid) references broker_config.solutions (id)
            on delete cascade,
    constraint fk_codeset_zone
        foreign key (zoneid) references broker_config.zones (id)
            on delete cascade
);

create table broker_config.codeitems
(
    id              char(36)                            not null
        primary key,
    codesetid       char(36)                            not null,
    source          varchar(2048)                       null,
    namespace       varchar(2048)                       not null,
    code            varchar(32)                         not null,
    value           varchar(128)                        not null,
    description     varchar(1024)                       null,
    definition      varchar(4096)                       null,
    action          varchar(36)                         not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unq_value_codeset
        unique (codesetid, value),
    constraint fk_codeitem_codeset
        foreign key (codesetid) references broker_config.codesets (id)
            on delete cascade
);

create table broker_config.codeitemaliases
(
    id              char(36)                             not null
        primary key,
    codeitemid      char(36)                             not null,
    source          varchar(2048)                        null,
    namespace       varchar(2048)                        not null,
    value           varchar(128)                         not null,
    old             tinyint(1) default 0                 not null,
    official        tinyint(1) default 0                 not null,
    lastUpdatedTime timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp  default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unq_value_codeitem
        unique (codeitemid, value),
    constraint fk_alias_codeitem
        foreign key (codeitemid) references broker_config.codeitems (id)
            on delete cascade
);

create index fk_alias_codeitem_idx
    on broker_config.codeitemaliases (codeitemid);

create index fk_codeitem_codeset_idx
    on broker_config.codeitems (codesetid);

create index fk_codeset_zone_idx
    on broker_config.codesets (zoneid);

alter table broker_config.contexts
    add constraint fk_context_zone
        foreign key (zoneid) references broker_config.zones (id)
            on delete cascade;

create table broker_config.environments
(
    id                                char(36)                             not null
        primary key,
    sharedsecret                      varchar(128)                         null,
    shared_secret_set_date            timestamp  default CURRENT_TIMESTAMP not null,
    shared_secret_expiration_override timestamp                            null,
    old_secret                        varchar(128)                         null,
    no_shared_secret_expiration       tinyint(1) default 0                 not null,
    issif2                            tinyint(1) default 0                 null,
    is_sif2_multi_zone                tinyint(1) default 0                 null,
    sif2version                       varchar(16)                          null,
    cidrentries                       varchar(255)                         null,
    tags                              varchar(255)                         null,
    publicidentifier                  varchar(255)                         null,
    sessiontoken                      varchar(64)                          null,
    fingerprint                       char(36)                             null,
    solutionid                        char(36)                             not null,
    appinfo_adapter_iconuri           varchar(128)                         null,
    appinfo_adapter_name              varchar(45)                          null,
    appinfo_adapter_version           varchar(45)                          null,
    appinfo_adapter_vendor            varchar(45)                          null,
    appinfo_appkey                    varchar(128)                         null,
    appinfo_app_iconuri               varchar(128)                         null,
    appinfo_app_name                  varchar(45)                          null,
    appinfo_app_version               varchar(45)                          null,
    appinfo_app_vendor                varchar(45)                          null,
    appinfo_dmnamespace               varchar(128)                         null,
    appinfo_infraversion              varchar(45)                          null,
    appinfo_transport                 varchar(14)                          null,
    authmethod                        varchar(14)                          null,
    consumername                      varchar(128)                         null,
    defaultzoneid                     char(36)                             null,
    instanceid                        varchar(45)                          null,
    type                              varchar(16)                          null,
    usertoken                         varchar(128)                         null,
    lastUpdatedTime                   timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime                       timestamp  default CURRENT_TIMESTAMP not null,
    status                            char(36)   default 'NEW'             not null,
    defaultProviderURL                varchar(512)                         null,
    sticky                            tinyint(1) default 0                 null,
    environmentKey                    varchar(255)                         null,
    oldSecret                         varchar(128)                         null,
    settings_hide_queues              tinyint(1) default 0                 null,
    environmentscol                   varchar(45)                          null,
    constraint environmentKey_UNIQUE
        unique (environmentKey),
    constraint unique_sessiontoken
        unique (solutionid, sessiontoken),
    constraint unique_solution_appkey
        unique (appinfo_appkey, solutionid),
    constraint unique_solution_consumername
        unique (solutionid, consumername),
    constraint unique_solution_fingerprint
        unique (fingerprint, solutionid),
    constraint unique_solution_publicidentifier
        unique (solutionid, publicidentifier),
    constraint fk_env_defaultZone
        foreign key (defaultzoneid) references broker_config.zones (id),
    constraint fk_env_solution
        foreign key (solutionid) references broker_config.solutions (id)
            on delete cascade
);

create index fk_env_defaultZone_idx
    on broker_config.environments (defaultzoneid);

create index fk_env_solution_idx
    on broker_config.environments (solutionid);

create index keyIdx
    on broker_config.environments (environmentKey);

create table broker_config.field_validations
(
    id                      char(36)                            not null
        primary key,
    `key`                   varchar(128)                        not null,
    name                    varchar(255)                        not null,
    description             varchar(512)                        not null,
    solution_id             char(36)                            not null,
    zone_id                 char(36)                            null,
    context_name            varchar(255)                        null,
    service_name            varchar(128)                        not null,
    error_level             varchar(32)                         not null,
    scope                   varchar(64)                         not null,
    type                    varchar(64)                         not null,
    execution_type          varchar(32)                         not null,
    schedule_cron           varchar(32)                         null,
    failure_processing_type varchar(32)                         null,
    replacement_value       varchar(128)                        null,
    script                  varchar(4096)                       null,
    query                   varchar(2048)                       null,
    field                   varchar(512)                        null,
    message                 varchar(512)                        not null,
    lastUpdatedTime         timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime             timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unique_solution_key
        unique (`key`, solution_id),
    constraint unique_solution_name
        unique (name, solution_id),
    constraint fk_fieldValidation_solution
        foreign key (solution_id) references broker_config.solutions (id)
            on delete cascade,
    constraint fk_fieldValidation_zone
        foreign key (zone_id) references broker_config.zones (id)
            on delete cascade
);

create table broker_config.field_validation_excludes
(
    id                  char(36)                            not null
        primary key,
    field_validation_id char(36)                            not null,
    name                varchar(255)                        not null,
    description         varchar(512)                        not null,
    zone_id             char(36)                            null,
    context_id          char(36)                            null,
    service_id          char(36)                            null,
    lastUpdatedTime     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime         timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint fk_fieldValidation_context
        foreign key (context_id) references broker_config.contexts (id)
            on delete cascade,
    constraint fk_fieldValidation_service
        foreign key (service_id) references broker_config.services (id)
            on delete cascade,
    constraint fk_fieldValidationsExludes_fieldValidation
        foreign key (field_validation_id) references broker_config.field_validations (id)
            on delete cascade,
    constraint fk_fieldValidationsExludes_zone
        foreign key (zone_id) references broker_config.zones (id)
            on delete cascade
);

create index fk_fieldValidation_context_idx
    on broker_config.field_validation_excludes (context_id);

create index fk_fieldValidation_service_idx
    on broker_config.field_validation_excludes (service_id);

create index fk_fieldValidationsExludes_fieldValidation_idx
    on broker_config.field_validation_excludes (field_validation_id);

create index fk_fieldValidationsExludes_zone_idx
    on broker_config.field_validation_excludes (zone_id);

create index fk_fieldValidation_solution_idx
    on broker_config.field_validations (solution_id);

create index fk_fieldValidation_zone_idx
    on broker_config.field_validations (zone_id);

create table broker_config.providers
(
    id                    char(36)                            not null
        primary key,
    serviceid             char(36)                            not null,
    endpoint              varchar(512)                        null,
    providerenvironmentid char(36)                            not null,
    lastUpdatedTime       timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime           timestamp default CURRENT_TIMESTAMP not null,
    providerKey           varchar(255)                        null,
    constraint providerKey_UNIQUE
        unique (providerKey),
    constraint unique_service
        unique (serviceid),
    constraint fk_provider_providerenvironment
        foreign key (providerenvironmentid) references broker_config.environments (id)
            on delete cascade,
    constraint fk_provider_service
        foreign key (serviceid) references broker_config.services (id)
            on delete cascade
);

create index fk_provider_providerenvironment_idx
    on broker_config.providers (providerenvironmentid);

create index fk_provider_service_idx
    on broker_config.providers (serviceid);

create index keyIdx
    on broker_config.providers (providerKey);

create table broker_config.provisionRequests
(
    id                  char(36)                            not null
        primary key,
    environmentid       char(36)                            not null,
    description         varchar(512)                        null,
    lastUpdatedTime     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime         timestamp default CURRENT_TIMESTAMP not null,
    completionstatus    char(36)                            null,
    provisionRequestKey varchar(255)                        null,
    constraint id_UNIQUE
        unique (id),
    constraint provisionRequestKey_UNIQUE
        unique (provisionRequestKey),
    constraint fk_provisionrequest_environment
        foreign key (environmentid) references broker_config.environments (id)
            on delete cascade
);

create index fk_provisionrequest_environment_idx
    on broker_config.provisionRequests (environmentid);

create index keyIdx
    on broker_config.provisionRequests (provisionRequestKey);

create table broker_config.provisionedZones
(
    id                 char(36)                            not null
        primary key,
    environmentid      char(36)                            null,
    lastUpdatedTime    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime        timestamp default CURRENT_TIMESTAMP not null,
    zoneid             char(36)                            not null,
    provisionrequestid char(36)                            null,
    type               char(36)                            not null,
    constraint unique_zoneid
        unique (environmentid, zoneid),
    constraint fk_provisionedzone_environment
        foreign key (environmentid) references broker_config.environments (id)
            on delete cascade,
    constraint fk_provisionedzone_provisionrequest
        foreign key (provisionrequestid) references broker_config.provisionRequests (id)
            on delete cascade,
    constraint fk_provisionedzone_zone
        foreign key (zoneid) references broker_config.zones (id)
            on delete cascade
);

create table broker_config.provisionedZoneServices
(
    id                char(36)                            not null
        primary key,
    provisionedzoneid char(36)                            not null,
    serviceid         char(36)                            not null,
    adminright        char(36)                            null,
    createright       char(36)                            null,
    deleteright       char(36)                            null,
    updateright       char(36)                            null,
    queryright        char(36)                            null,
    provideright      char(36)                            null,
    subscriberight    char(36)                            null,
    lastUpdatedTime   timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime       timestamp default CURRENT_TIMESTAMP not null,
    endpoint          varchar(512)                        null,
    constraint unique_provisionedzone_service
        unique (provisionedzoneid, serviceid),
    constraint unique_provisionedzoneservices_serviceid_zoneid
        unique (serviceid, provisionedzoneid),
    constraint fk_provisionedzoneservices_provisionedzone
        foreign key (provisionedzoneid) references broker_config.provisionedZones (id)
            on delete cascade,
    constraint fk_provisionedzoneservices_service
        foreign key (serviceid) references broker_config.services (id)
            on delete cascade
);

create index fk_provisionedzoneservices_provisionedzoneid_idx
    on broker_config.provisionedZoneServices (provisionedzoneid);

create index fk_provisionedzoneservices_service_idx
    on broker_config.provisionedZoneServices (serviceid);

create index fk_provisionedzone_environment_idx
    on broker_config.provisionedZones (environmentid);

create index fk_provisionedzone_provisionrequest_idx
    on broker_config.provisionedZones (provisionrequestid);

create index fk_provisionedzone_zone_idx
    on broker_config.provisionedZones (zoneid);

create table broker_config.queues
(
    id                       char(255)                             not null
        primary key,
    environmentid            char(36)                              not null,
    intendedzoneid           char(36)                              null,
    queuetype                varchar(45) default 'GENERIC'         not null,
    encryptionlevel          char(36)    default 'MEDIUM'          not null,
    ttlminutes               int         default 10080             not null,
    name                     varchar(255)                          not null,
    polling                  char(36)    default 'IMMEDIATE'       not null,
    minwaittime              int         default 1                 not null,
    idletimeout              int         default 1                 not null,
    maxconcurrentconnections int         default 1                 not null,
    lastUpdatedTime          timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime              timestamp   default CURRENT_TIMESTAMP not null,
    queueKey                 varchar(255)                          null,
    ownerURI                 varchar(255)                          null,
    constraint id_UNIQUE
        unique (id),
    constraint queueKey_UNIQUE
        unique (queueKey),
    constraint unq_name_environmentid
        unique (environmentid, name),
    constraint fk_queue_environment
        foreign key (environmentid) references broker_config.environments (id)
            on delete cascade,
    constraint fk_queue_intendedzone
        foreign key (intendedzoneid) references broker_config.zones (id)
            on delete set null
);

create index fk_queue_environment_idx
    on broker_config.queues (environmentid);

create index fk_queue_zone_idx
    on broker_config.queues (intendedzoneid);

create index keyIdx
    on broker_config.queues (queueKey);

create index nameIdx
    on broker_config.queues (name);

create table broker_config.roles
(
    id              char(36)                            not null
        primary key,
    domainid        char(36)                            null,
    solutionid      char(36)                            null,
    environmentid   char(36)                            null,
    name            varchar(128)                        not null,
    description     varchar(1024)                       null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint id_UNIQUE
        unique (id),
    constraint unique_name_domain
        unique (name, domainid),
    constraint unique_name_environment
        unique (name, environmentid),
    constraint unique_name_solution
        unique (name, solutionid),
    constraint fk_role_domain
        foreign key (domainid) references broker_config.domains (id)
            on delete cascade,
    constraint fk_role_environment
        foreign key (environmentid) references broker_config.environments (id)
            on delete cascade,
    constraint fk_role_solution
        foreign key (solutionid) references broker_config.solutions (id)
            on delete cascade
);

create index fk_role_domain_idx
    on broker_config.roles (domainid);

create index fk_role_environment_idx
    on broker_config.roles (environmentid);

create index fk_role_solution_idx
    on broker_config.roles (solutionid);

alter table broker_config.solutions
    add constraint fk_solution_defaultzone
        foreign key (defaultzoneid) references broker_config.zones (id)
            on delete set null;

create table broker_config.subscriptions
(
    id              char(36)                            not null
        primary key,
    serviceid       char(36)                            not null,
    queueid         char(255)                           not null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    subscriptionKey varchar(255)                        null,
    constraint subscriptionKey_UNIQUE
        unique (subscriptionKey),
    constraint unique_service_queue
        unique (serviceid, queueid),
    constraint fk_subscription_queue
        foreign key (queueid) references broker_config.queues (id)
            on delete cascade,
    constraint fk_subscription_service
        foreign key (serviceid) references broker_config.services (id)
            on delete cascade
);

create index fk_subscription_queue_idx
    on broker_config.subscriptions (queueid);

create index fk_subscription_service_idx
    on broker_config.subscriptions (serviceid);

create index keyIdx
    on broker_config.subscriptions (subscriptionKey);

create table broker_config.userrole
(
    userid          char(36)                            not null,
    roleid          char(36)                            null,
    lastUpdatedTime timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    createdTime     timestamp default CURRENT_TIMESTAMP not null,
    constraint unique_user_role
        unique (userid, roleid),
    constraint fk_userrole_role
        foreign key (roleid) references broker_config.roles (id)
            on delete cascade,
    constraint fk_userrole_user
        foreign key (userid) references broker_config.users (id)
            on delete cascade
);

create index fk_userrole_role_idx
    on broker_config.userrole (roleid);

create index fk_userrole_user_idx
    on broker_config.userrole (userid);

create index fk_zone_defaultcontext_idx
    on broker_config.zones (defaultcontextid);

create index fk_zone_solution_idx
    on broker_config.zones (solutionid);

create index keyIdx
    on broker_config.zones (zoneKey);

create index nameIdx
    on broker_config.zones (name);


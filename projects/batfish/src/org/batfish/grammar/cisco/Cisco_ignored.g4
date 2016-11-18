parser grammar Cisco_ignored;

import Cisco_common;

options {
   tokenVocab = CiscoLexer;
}

null_block_stanza
:
   NO?
   (
      AAA_SERVER
      | ACCESS_GROUP
      | ACCESS
      | ACL_POLICY
      | ACLLOG
      | ADMIN
      | ALLOW
      | APPLETALK
      | AS_PATH_SET
      | ATM
      | BASH
      | BFD
      | BGP DISABLE_ADVERTISEMENT
      | BSD_CLIENT
      | BSD_USERNAME
      | BUFFERS
      | CALL_HOME
      | CAM_ACL
      | CAM_PROFILE
      | CEF
      | CHAT_SCRIPT
      | CISP
      | CLI
      | CLNS
      | CLOCK
      | COMMIT
      | CONFDCONFIG
      | CONFIGURATION
      | CONFIGURE
      | CONTROLLER
      | COAP
      | COPP
      | COPY
      | COS_QUEUE_GROUP
      | CPD
      | CRYPTO
      | CTL_FILE
      | DAEMON
      | DCB
      | DCB_BUFFER_THRESHOLD
      | DEBUG
      | DEFAULT_MAX_FRAME_SIZE
      | DEFAULT_VALUE
      | DHCPRELAY
      | DO STOP
      | DOMAIN
      | DOT11
      | DOT1X
      | DOT1X_ENABLE
      | DUAL_MODE_DEFAULT_VLAN
      | DYNAMIC_ACCESS_POLICY_RECORD
      | ENABLE
      | ENABLE_ACL_COUNTER
      | ENABLE_QOS_STATISTICS
      | END
      | ETHERNET
      | EVENT
      | EVENT_HANDLER
      | EXCEPTION_SLAVE
      | EXIT
      | FEATURE_SET
      | FEX
      | FLOW
      | FPD
      | GATEKEEPER
      | GATEWAY
      | GLOBAL_PORT_SECURITY
      | GROUP
      | GROUP_POLICY
      | HASH_ALGORITHM
      | HPM
      | HSRP
      | HW_SWITCH
      | INSTALL
      | INTERFACE BREAKOUT
      |
      (
         IP
         (
            (
               ACCESS_LIST LOGGING
            )
            | ACCOUNTING_LIST
            | ACCOUNTING_THRESHOLD
            | BOOTP_RELAY
            | DECAP_GROUP
            | DHCP
            | DNS
            | ECMP_GROUP
            | FLOW_AGGREGATION
            | FLOW_CAPTURE
            | FLOW_SAMPLING_MODE
            | FLOW_TOP_TALKERS
            | GLOBAL_MTU
            | HARDWARE
            | ICMP_ERRORS
            | INSPECT
            | NAME_SERVER
            |
            (
               OSPF NAME_LOOKUP
            )
            | PIM
            | POLICY_LIST
            | RATE_LIMIT
            | RECEIVE
            | REFLEXIVE_LIST
            | ROUTER_ID
            | RSVP
            | SDR
            | SLA
            | SOURCE
            | SYSLOG
            | VIRTUAL_ROUTER
            |
            (
               VRF ~NEWLINE
            )
         )
      )
      | IPC
      | IPSLA
      |
      (
         IPV4
         (
            ASSEMBLER
            | CONFLICT_POLICY
            | HARDWARE
            | UNNUMBERED
            | VIRTUAL
         )
      )
      |
      (
         IPV6
         (
            CONFLICT_POLICY
            | GLOBAL_MTU
            | ENABLE_ACL_CAM_SHARING
            | HARDWARE
            | MROUTE
            | NEIGHBOR
         )
      )
      | KEY
      | KRON
      | L2TP_CLASS
      | LACP
      | LAG
      | LINECARD
      | LOAD_BALANCE
      | LOGIN
      | MAC
      | MAC_LEARN
      | MACRO
      | MANAGEMENT_ACCESS
      | MAP_CLASS
      | MAP_LIST
      | MAXIMUM_PATHS
      | MEDIA_TERMINATION
      | MENU
      | MLAG
      | MODULE
      | MONITOR
      | MONITOR_INTERFACE
      |
      (
         MPLS
         (
            (
               IP
               | IPV6
               | LDP ~NEWLINE
            )
            | OAM
            | TRAFFIC_ENG
         )
      )
      | MULTI_CONFIG
      | MULTICAST
      |
      (
         NO
         (
            (
               AAA
               (
                  NEW_MODEL
                  | ROOT
                  |
                  (
                     USER DEFAULT_ROLE
                  )
               )
            )
            | CLASS_MAP
            |
            (
               IP
               (
                  AS_PATH
               )
            )
            | LOGGING
            | SSH
         )
      )
      | NLS
      | NO_BANNER
      | NO_L4R_SHIM
      | NSR
      | NV
      | ONE
      | OPENFLOW
      | OPTICAL_MONITOR
      | PASSWORD_POLICY
      | PLAT
      | PLATFORM
      | POLICY_MAP
      | POLICY_MAP_INPUT
      | POLICY_MAP_OUTPUT
      | PORT_PROFILE
      | POWEROFF
      | POWER_MGR
      | PRIORITY_FLOW_CONTROL
      | PROTOCOL
      | PSEUDOWIRE_CLASS
      | PTP
      | QOS_MAPPING
      | QOS_POLICY
      | QOS_POLICY_OUTPUT
      | REDUNDANCY
      | RELOAD_TYPE
      | REMOVED
      | RMON
      | ROLE
      | ROUTE_ONLY
      | ROUTER
      (
         LOG
         | VRRP
      )
      | RP
      | RX_COS_SLOT
      | SAMPLER
      | SAMPLER_MAP
      | SAP
      | SCCP
      | SCHEDULE
      | SDR
      | SENSOR
      | SERVICE_CLASS
      | SFLOW
      | SLOT
      | SLOT_TABLE_COS
      | SPANNING_TREE
      | STACK_MAC
      | STACK_UNIT
      | STATISTICS
      | SVCLC
      | SWITCH
      | SWITCH_PROFILE
      | SWITCH_TYPE
      | SYSLOGD
      | SYSTEM_INIT
      | SYSTEM_MAX
      | TABLE_MAP
      | TACACS
      | TACACS_SERVER
      | TAG_TYPE
      | TAP
      | TASKGROUP
      | TCP
      | TEMPLATE
      | TERMINAL
      | TIME_RANGE
      | TIMEOUT
      | TFTP
      | TLS_PROXY
      | TRACE
      | TRACK
      | TRANSCEIVER
      | TRANSCEIVER_TYPE_CHECK
      | TRANSPARENT_HW_FLOODING
      | TUNNEL_GROUP
      | UDF
      | USERGROUP
      | USERNAME
      | VDC
      | VER
      |
      (
         VLAN
         (
            DEC
            | ACCESS_MAP
         )
      )
      | VLAN_GROUP
      | VLAN_POLICY
      | VLT
      | VOICE
      | VOICE_PORT
      | VPC
      | VPDN_GROUP
      | VXLAN
      | VTY_POOL
      | WEBVPN
      | WISM
      | WRED_PROFILE
      | WSMA
      | XDR
      | XML
   ) ~NEWLINE* NEWLINE
   (
      description_line
      | null_block_substanza
      | null_block_substanza_full
      | unrecognized_line
   )*
;

null_block_substanza
:
   (
      NO?
      (
         ACCEPT_DIALIN
         | ACCEPT_LIFETIME
         | ACCOUNTING
         | ACCOUNTING_SERVER_GROUP
         | ACTION
         | ACTIVE
         | ADD_VLAN
         | ADDRESS
         | ADDRESS_POOL
         | ADDRESS_POOLS
         | ADMINISTRATIVE_WEIGHT
         | ADMIN_STATE
         | ADVERTISE
         | AESA
         | AGE
         | AIS_SHUT
         | ALARM_REPORT
         | ALERT_GROUP
         | ALLOW_CONNECTIONS
         | ALWAYS_ON_VPN
         | ANYCONNECT
         | ANYCONNECT_ESSENTIALS
         | APPLICATION
         | ARCHIVE_LENGTH
         | ARCHIVE_SIZE
         | ASSOC_RETRANSMIT
         | ASSOCIATE
         | ASSOCIATION
         | AUTHENTICATION
         | AUTHENTICATION_SERVER_GROUP
         | AUTHORIZATION_REQUIRED
         | AUTHORIZATION_SERVER_GROUP
         | AUTO_RECOVERY
         | AUTO_SYNC
         | BACK_UP
         | BACKGROUND_ROUTES_ENABLE
         | BACKUPCRF
         | BANDWIDTH
         | BANDWIDTH_PERCENTAGE
         |
         (
            BANNER
            (
               NONE
               | VALUE
            )
         )
         | BIND
         | BRIDGE
         | BRIDGE_PRIORITY
         | BUCKETS
         | CABLELENGTH
         | CACHE
         | CACHE_TIMEOUT
         | CALL
         | CALLER_ID
         | CAS_CUSTOM
         | CDP_URL
         | CERTIFICATE
         | CHANNEL_GROUP
         | CHANNELIZED
         | CIR
         | CLASS
         | CLIENT_GROUP
         | CLOCK
         | CODEC
         | COLLECT
         | COMMAND
         | CONFORM_ACTION
         | CONGESTION_CONTROL
         | CONNECT_SOURCE
         | CONTEXT
         | CONTACT_EMAIL_ADDR
         | CONTACT_NAME
         | CONTRACT_ID
         | CREDENTIALS
         | CRL
         | CRYPTOGRAPHIC_ALGORITHM
         | CSD
         | CUSTOMER_ID
         | DBL
         | DEADTIME
         | DEFAULT
         | DEFAULT_ACTION
         | DEFAULT_DOMAIN
         | DEFAULT_GROUP_POLICY
         | DEFAULT_ROUTER
         | DELAY
         | DELETE_DYNAMIC_LEARN
         | DENY
         | DEPLOY
         | DESCRIPTION
         | DESTINATION
         | DESTINATION_PATTERN
         | DESTINATION_SLOT
         | DEVICE
         | DIAGNOSTIC
         | DISABLE
         | DISTRIBUTION
         | DNS_SERVER
         | DOMAIN_ID
         | DOMAIN_NAME
         | DROP
         | DSCP
         | DSCP_VALUE
         | DS0_GROUP
         | DUAL_ACTIVE
         | ECHO
         | ECHO_CANCEL
         | EGRESS
         | ENABLE
         | ENABLED
         | ENCAPSULATION
         | ENCRYPTION
         | END_POLICY_MAP
         | ENROLLMENT
         | ERROR_RECOVERY
         | ERSPAN_ID
         | ESCAPE_CHARACTER
         | EXCEED_ACTION
         | EXIT
         | EXPECT
         | EXPORT
         | EXPORT_PROTOCOL
         | EXPORTER
         | EXTENDED_COUNTERS
         | FABRIC
         | FAILED
         | FAIR_QUEUE
         | FALLBACK_DN
         | FIELDS
         | FILE_BROWSING
         | FILE_ENTRY
         | FILE_SIZE
         | FLUSH_AT_ACTIVATION
         | FORWARD_DIGITS
         | FQDN
         | FRAMING
         | FREQUENCY
         | FT
         | G709
         | GATEWAY
         | GID
         | GROUP
         | GROUP_ALIAS
         | GROUP_LOCK
         | GROUP_POLICY
         | GROUP_URL
         | GW_TYPE_PREFIX
         | GUEST_MODE
         | H225
         | H323
         | HEARTBEAT_INTERVAL
         | HEARTBEAT_TIME
         | HELPER_ADDRESS
         | HIDDEN_LITERAL
         | HIDDEN_SHARES
         | HIDEKEYS
         | HIGH_AVAILABILITY
         | HISTORY
         | HOMEDIR
         | HOPS_OF_STATISTICS_KEPT
         | ICMP_ECHO
         | ID_MISMATCH
         | ID_RANDOMIZATION
         | IDLE
         | IDLE_TIMEOUT
         | IMPORT
         | INCOMING
         | INGRESS
         | INSERVICE
         | INSPECT
         | INSTANCE
         | INTEGRITY
         |
         (
            INTERFACE POLICY
         )
         | INTERVAL
         |
         (
            (
               IP
               | IPV6
            )
            (
               ACCESS_GROUP
               | ADDRESS
               | ARP
               | FLOW
            )
         )
         | IPSEC_UDP
         | IPX
         | IPV6_ADDRESS_POOL
         | ISAKMP
         | ISSUER_NAME
         | FREQUENCY
         | KEEPALIVE_ENABLE
         | KEEPOUT
         | KEY_STRING
         | KEYPAIR
         | KEYPATH
         | KEYRING
         | L2TP
         | LACP_TIMEOUT
         | LEASE
         | LENGTH
         | LIFE
         | LIMIT_RESOURCE
         | LINECODE
         | LLDP
         | LOCAL_INTERFACE
         | LOCAL_IP
         | LOCAL_PORT
         | LOG
         | LPTS
         | LRQ
         | MAC_ADDRESS
         | MAIL_SERVER
         | MAIN_CPU
         | MAP
         | MATCH
         | MAX_ASSOCIATIONS
         | MAXIMUM
         | MBSSID
         | MEDIA
         | MEMBER
         | MESH_GROUP
         | MESSAGE_LENGTH
         | MODE
         | MONITORING
         | MSDP_PEER
         | MSIE_PROXY
         | MTU
         | NAME
         | NAMESPACE
         | NAT
         | NATPOOL
         | NEGOTIATE
         | NETWORK
         | NODE
         | NOTIFICATION_TIMER
         | NOTIFY
         | OBJECT
         | OPEN
         | OPERATION
         | OPTION
         | OPTIONS
         | OPS
         | ORIGINATOR_ID
         | OUI
         | PARAMETERS
         | PARENT
         | PARITY
         | PASSWORD
         | PASSWORD_STORAGE
         | PATH_ECHO
         | PATH_JITTER
         | PATH_RETRANSMIT
         | PATHS_OF_STATISTICS_KEPT
         | PAUSE
         | PCP
         | PCP_VALUE
         | PEER_ADDRESS
         | PEER_CONFIG_CHECK_BYPASS
         | PEER_GATEWAY
         | PEER_ID_VALIDATE
         | PEER_KEEPALIVE
         | PEER_LINK
         | PEER_SWITCH
         | PERIODIC
         | PERMIT
         | PERSISTENT
         | PHONE_NUMBER
         | PHYSICAL_PORT
         | PICKUP
         | PINNING
         | PM
         | POLICE
         | POLICY
         | POLICY_LIST
         | POLICY_MAP
         | PORT_NAME
         | PORTS
         | PRECEDENCE
         | PREDICTOR
         | PRE_SHARED_KEY
         | PREEMPT
         | PREFERRED_PATH
         | PREFIX
         | PRF
         | PRI_GROUP
         | PRIMARY_PORT
         | PRIMARY_PRIORITY
         | PRIORITY
         | PRIVATE_VLAN
         | PRIVILEGE
         | PROACTIVE
         | PROBE
         | PROFILE
         | PROPOSAL
         | PROTOCOL
         | PROTOCOL_VIOLATION
         | PROVISION
         | PROXY_SERVER
         | QUEUE
         | QUEUE_BUFFERS
         | QUEUE_LIMIT
         | RANDOM
         | RANDOM_DETECT
         | RANDOM_DETECT_LABEL
         | RD
         | REACT
         | REACTION
         | REAL
         | RECEIVE
         | RECORD
         | RECORD_ENTRY
         | REDISTRIBUTE
         | RELOAD
         | RELOAD_DELAY
         | REMARK
         | REMOTE_AS
         | REMOTE_IP
         | REMOTE_PORT
         | REMOTE_SPAN
         | REQUEST
         | REQUEST_DATA_SIZE
         | RESOURCES
         | RESPONDER
         | RETRANSMIT
         | RETRANSMIT_TIMEOUT
         | RETRIES
         | REVERSE_ROUTE
         | REVISION
         | RING
         | ROLE
         | ROUTE
         | ROUTE_TARGET
         | ROUTER_INTERFACE
         | RP_ADDRESS
         | RULE
         | SA_FILTER
         | SAMPLES_OF_HISTORY_KEPT
         | SATELLITE
         | SCHEME
         | SECRET
         | SECURE_MAC_ADDRESS
         | SEND_LIFETIME
         | SENDER
         | SEQUENCE
         | SERIAL_NUMBER
         | SERVER
         | SERVERFARM
         | SERVER_PRIVATE
         | SERVICE_POLICY
         | SERVICE_QUEUE
         | SERVICE_TYPE
         | SESSION
         | SET
         | SEVERITY
         | SHAPE
         | SHUT
         | SHUTDOWN
         | SIGNAL
         | SINGLE_CONNECTION
         | SINGLE_ROUTER_MODE
         | SITE_ID
         | SLOT
         | SMTP
         | SORT_BY
         | SOURCE
         | SOURCE_INTERFACE
         | SOURCE_IP_ADDRESS
         | SPANNING_TREE
         | SPEED
         | SPLIT_TUNNEL_NETWORK_LIST
         | SPLIT_TUNNEL_POLICY
         | SSH_KEYDIR
         | START_TIME
         | STATISTICS
         | STICKY
         | STP
         | STREET_ADDRESS
         | STS_1
         | SUBJECT_NAME
         | SUBSCRIBE_TO_ALERT_GROUP
         | SVC
         | SWITCHBACK
         | SWITCHPORT
         | SYNC
         | SYSTEM_PRIORITY
         | TAG
         | TAGGED
         | TASK
         | TASK_SPACE_EXECUTE
         | TASKGROUP
         | TB_VLAN1
         | TB_VLAN2
         | TCP_CONNECT
         | THRESHOLD
         | TIMEOUT
         | TIMEOUTS
         | TIMER
         | TIMING
         | TM_VOQ_COLLECTION
         | TOP
         | TOS
         | TRACKING_PRIORITY_INCREMENT
         | TRANSLATION_PROFILE
         | TRANSPORT
         | TRIGGER
         | TRUNK
         | TRUNK_THRESHOLD
         | TRUST
         | TTL_THRESHOLD
         | TUNNEL
         | TUNNEL_GROUP
         | TUNNEL_GROUP_LIST
         | TYPE
         | UDP_JITTER
         | UID
         | UNTAGGED
         | URL_LIST
         | USE_VRF
         | USER_MESSAGE
         | USER_STATISTICS
         | USERS
         | VERIFY_DATA
         | VERSION
         | VIOLATE_ACTION
         | VIOLATION
         | VIRTUAL
         | VIRTUAL_ROUTER
         | VIRTUAL_TEMPLATE
         | VM_CPU
         | VM_MEMORY
         | VPN_FILTER
         | VPN_GROUP_POLICY
         | VPN_IDLE_TIMEOUT
         | VPN_SESSION_TIMEOUT
         | VPN_SIMULTANEOUS_LOGINS
         | VPN_TUNNEL_PROTOCOL
         | VSERVER
         | WAVELENGTH
         | WINS_SERVER
         | WITHOUT_CSD
         | WRED
         | XML_CONFIG
         | ZONE
      )
      (
         remaining_tokens += ~NEWLINE
      )* NEWLINE
   )
;

null_block_substanza_full
:
   (
      (
         VLAN DEC
         (
            CLIENT
            | SERVER
         )
      )
      |
      (
         VRF variable
      )
   ) NEWLINE
;

null_standalone_stanza_DEPRECATED_DO_NOT_ADD_ITEMS
:
   (
      NO
   )?
   (
      ABSOLUTE_TIMEOUT
      |
      (
         ACCESS_LIST
         (
            (
               DEC REMARK
            )
            | VARIABLE
         )
      )
      | ACCOUNTING_PORT
      | ACTION
      | ALIAS
      | AP
      | AQM_REGISTER_FNF
      | ARP
      | ASA
      | ASDM
      | ASSOCIATE
      | ASYNC_BOOTP
      | AUTHENTICATION
      | AUTHENTICATION_PORT
      | AUTO
      | BOOT
      | BOOT_END_MARKER
      | BOOT_START_MARKER
      | BRIDGE
      | CALL
      | CARD
      | CCM_MANAGER
      | CDP
      | CFS
      | CIPC
      | CLOCK
      | CLUSTER
      | CNS
      | CODEC
      | CONFIG_REGISTER
      | CONSOLE
      | CRL
      | CTS
      | DEC
      | DEFAULT
      | DESCRIPTION
      | DEVICE_SENSOR
      | DHCPD
      | DIAGNOSTIC
      | DIALER_LIST
      | DISABLE
      | DNS
      | DNS_GUARD
      | DOMAIN_NAME
      | DSP
      | DSPFARM
      | DSS
      | ENCR
      | ENROLLMENT
      | ENVIRONMENT
      | ERRDISABLE
      | ESCAPE_CHARACTER
      | EXCEPTION
      | EXEC
      | FABRIC
      | FACILITY_ALARM
      | FILE
      | FIREWALL
      | FIRMWARE
      | FLOWCONTROL
      | FRAME_RELAY
      | FQDN
      | FTP
      | FTP_SERVER
      | HARDWARE
      | HASH
      | HISTORY
      | HOST
      | HTTP
      | HW_MODULE
      | ICMP
      | ICMP_OBJECT
      | IDENTITY
      | INACTIVITY_TIMER
      |
      (
         IP
         (
            ADDRESS_POOL
            | ADMISSION
            | ALIAS
            | ARP
            | AUDIT
            | AUTH_PROXY
            | BOOTP
            | BGP_COMMUNITY
            | CEF
            | CLASSLESS
            | DEFAULT_NETWORK
            | DEVICE
            | DOMAIN
            | DOMAIN_LIST
            | DOMAIN_LOOKUP
            | DVMRP
            | EXTCOMMUNITY_LIST
            | FINGER
            | FLOW_CACHE
            | FLOW_EXPORT
            | FORWARD_PROTOCOL
            | FTP
            | GRATUITOUS_ARPS
            | HOST
            | HOST_ROUTING
            | HTTP
            | ICMP
            | IGMP
            | LOAD_SHARING
            | LOCAL
            | MFIB
            | MROUTE
            | MSDP
            | MULTICAST
            | MULTICAST_ROUTING
            | NAT
            | RADIUS
            | RCMD
            | ROUTING //might want to use this eventually

            | SAP
            | SCP
            | SLA
            | SUBNET_ZERO
            | TACACS
            | TCP
            | TELNET
            | TFTP
            | VERIFY
         )
      )
      | IP_ADDRESS_LITERAL
      |
      (
         IPV6
         (
            CEF
            | HOST
            | LOCAL
            | MFIB
            | MFIB_MODE
            | MLD
            | MULTICAST
            | MULTICAST_ROUTING
            | ND
            |
            (
               OSPF NAME_LOOKUP
            )
            | PIM
            | ROUTE
            | SOURCE_ROUTE
            | UNICAST_ROUTING
         )
      )
      | ISDN
      | KEYPAIR
      | KEYRING
      | LDAP_BASE_DN
      | LDAP_LOGIN
      | LDAP_LOGIN_DN
      | LDAP_NAMING_ATTRIBUTE
      | LDAP_SCOPE
      | LICENSE
      | LIFETIME
      | LLDP
      | LOCATION
      | MAC_ADDRESS_TABLE
      | MAXIMUM
      | MEMORY_SIZE
      | MGCP
      | MICROCODE
      | MLS
      | MODE
      | MODEM
      | MTA
      | MTU
      | MULTILINK
      | MVR
      | NAME_SERVER
      | NAME
      | NAMES
      | NAT
      | NAT_CONTROL
      | NETCONF
      | NETWORK_OBJECT
      | NETWORK_CLOCK_PARTICIPATE
      | NETWORK_CLOCK_SELECT
      | OWNER
      | PAGER
      | PARSER
      | PARTICIPATE
      | PASSWORD
      | PERCENT
      | PHONE_PROXY
      | PLATFORM
      | PORT_CHANNEL
      | PORT_OBJECT
      | POWER
      | PRIORITY
      | PRIORITY_QUEUE
      | PRIVILEGE
      | PROCESS
      | PROMPT
      | PROTOCOL_OBJECT
      | QOS
      | QUIT
      | RADIUS_COMMON_PW
      | RADIUS_SERVER
      | RD
      | RECORD_ENTRY
      | REDIRECT_FQDN
      | RESOURCE
      | RESOURCE_POOL
      | REVERSE_ROUTE
      | REVOCATION_CHECK
      | ROUTE
      | ROUTE_TARGET
      | RSAKEYPAIR
      | RTR
      | SAME_SECURITY_TRAFFIC
      | SCHEDULER
      | SCRIPTING
      | SDM
      | SECURITY
      | SERVER_TYPE
      | SERVICE_POLICY
      | SETUP
      | SHELL
      | SMTP_SERVER
      | SNMP
      | SOURCE
      | SPANNING_TREE
      | SPD
      | SPE
      | SPEED
      | STOPBITS
      | SSL
      | STATIC
      | SUBJECT_NAME
      | SUBNET
      | SUBSCRIBER
      | SUBSCRIBE_TO
      | SYSOPT
      | SYSTEM
      | TAG_SWITCHING
      | TELNET
      | TFTP_SERVER
      | THREAT_DETECTION
      | TRANSLATE
      | TRANSPORT
      | TYPE
      | UDLD
      | UNABLE
      | UPGRADE
      | USER_IDENTITY
      | USE_VRF
      | VALIDATION_USAGE
      | VERSION
      |
      (
         VLAN
         (
            ACCESS_LOG
            | CONFIGURATION
            | DOT1Q
            | INTERNAL
         )
      )
      | VMPS
      | VPDN
      | VPN
      | VTP
      | VOICE_CARD
      | WLAN
      | X25
      | X29
      | XLATE
      | XML SERVER
   )
   (
      remaining_tokens += ~NEWLINE
   )* NEWLINE
;

unrecognized_block_stanza
:
   unrecognized_line null_block_substanza*
;

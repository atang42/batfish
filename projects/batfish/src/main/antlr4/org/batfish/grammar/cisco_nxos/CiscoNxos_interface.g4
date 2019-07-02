parser grammar CiscoNxos_interface;

import CiscoNxos_common;

options {
  tokenVocab = CiscoNxosLexer;
}

s_interface
:
  INTERFACE irange = interface_range NEWLINE
  (
    i_encapsulation
    | i_ip
    | i_no
    | i_null
    | i_switchport
  )*
;

i_encapsulation
:
  ENCAPSULATION DOT1Q vlan = restricted_vlan_id NEWLINE
;

i_ip
:
  IP i_ip_address
;

i_ip_address
:
  ADDRESS addr = interface_address SECONDARY? NEWLINE
;

i_no
:
  NO
  (
    i_no_shutdown
    | i_no_switchport
  )
;

i_no_shutdown
:
  SHUTDOWN NEWLINE
;

i_no_switchport
:
  SWITCHPORT NEWLINE
;

i_null
:
  NO?
  (
    IP REDIRECTS
  ) null_rest_of_line
;

i_switchport
:
  SWITCHPORT
  (
    i_switchport_access
    | i_switchport_trunk_allowed
    | i_switchport_trunk
  )
;

i_switchport_access
:
  ACCESS VLAN vlan = restricted_vlan_id NEWLINE
;

i_switchport_trunk
:
  TRUNK
  (
    i_switchport_trunk_allowed
    | i_switchport_trunk_native
  )
;

i_switchport_trunk_allowed
:
  ALLOWED VLAN
  (
    (
      ADD
      | REMOVE
    )? vlans = vlan_id_range
    | EXCEPT except = vlan_id
    | NONE
  ) NEWLINE
;

i_switchport_trunk_native
:
  NATIVE VLAN vlan = restricted_vlan_id NEWLINE
;

interface_range
:
  iname = interface_name
  (
    DASH last = uint8
  )?
;

/*
 * These two rules are added only for type safety; they force the Extractor to convert respecting
 * specific valid VLAN ranges (1-3967 vs. 1-4094) which differs based on the containing rule.
 *
 * When adding grammar using vlan IDs, choose the rule corresponding to the correct allowed range.
 */
restricted_vlan_id
:
// 1-3967
  UINT8
  | UINT16
;

vlan_id
:
// 1-4094
  UINT8
  | UINT16
;

vlan_id_range
:
  intervals += vlan_id_interval
  (
    COMMA intervals += vlan_id_interval
  )*
;

vlan_id_interval
:
  low = vlan_id
  (
    DASH high = vlan_id
  )?
;
package alarm.authz

default allow = false

allow {
	input.jwt.realm_access.roles[_] == "operator"
	input.action == "ACK"
	project_allowed
}

project_allowed {
	some p
	p := input.jwt.resource_access.alarm.projects[_]
	p == input.resource.project_id
}

#!/bin/bash
echo "=== MANAGEMENT MEMBERS ==="
mysql -uroot -p123456789 cvconnect-user-service -e "SELECT mm.id, mm.org_id, mm.user_id, mm.member_type, u.username FROM management_members mm JOIN users u ON u.id = mm.user_id;"

echo "=== ORG MEMBERS ==="
mysql -uroot -p123456789 cvconnect-user-service -e "SELECT om.id, om.org_id, om.user_id, om.role_code, u.username FROM org_members om JOIN users u ON u.id = om.user_id LIMIT 20;"

echo "=== CANDIDATES TABLE ==="
mysql -uroot -p123456789 cvconnect-user-service -e "SELECT c.id, c.user_id, u.username FROM candidates c JOIN users u ON u.id = c.user_id LIMIT 20;"

echo "=== POSTGRES: job_ad_candidate ==="
psql postgresql://postgres:123456789@localhost:5433/cvconnect_core_service -c "SELECT jac.id, jac.job_ad_id, jac.candidate_info_id, jac.candidate_status, ja.title, ja.org_id FROM job_ad_candidate jac JOIN job_ad ja ON ja.id = jac.job_ad_id ORDER BY jac.id;"

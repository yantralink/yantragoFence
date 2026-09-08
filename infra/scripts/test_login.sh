#!/bin/bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"9527028875","password":"yantrago"}' | python3 -m json.tool

# SCC_TUKANO

## Authors
Project developed for SCC 2024/25 - MEI

- João Brilha (70274) — j.brilha@campus.fct.unl.pt
- Eduardo Ervideira (72420) — e.ervideira@campus.fct.unl.pt

## Quick Start Guide

### Local Development Setup

1. Start Tomcat container:
```bash
docker run -ti --net=host smduarte/tomcat10
```

2. Deploy application:
```bash
mvn clean compile package tomcat7:redeploy
```

### Testing

```bash
# Run specific local test scenario
artillery run testing/local/users_create_get.yaml

# Or run cloud test scenario
artillery run testing/cloud/users_create_get.yaml
```


### Azure Functions Deployment

```bash
cd serverless
mvn clean package azure-functions:deploy
```

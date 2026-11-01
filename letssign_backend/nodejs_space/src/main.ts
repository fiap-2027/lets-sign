import { BadRequestException, Logger, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { NestExpressApplication } from '@nestjs/platform-express';
import { NextFunction, Request, Response, json, urlencoded } from 'express';
import { AppModule } from './app.module';
import { GlobalExceptionFilter } from './common/exception.filter';

function flatten(errors: any[]): string[] {
  const out: string[] = [];
  for (const e of errors) {
    if (e.constraints) {
      for (const [k, v] of Object.entries<string>(e.constraints)) {
        out.push(k === 'whitelistValidation' ? `O campo "${e.property}" não é permitido.` : v);
      }
    }
    if (e.children?.length) out.push(...flatten(e.children));
  }
  return out;
}

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, { bodyParser: false });
  const logger = new Logger('HTTP');
  app.set('trust proxy', true);
  app.use(json({ limit: '10mb' }));
  app.use(urlencoded({ extended: true, limit: '10mb' }));
  app.enableCors({ origin: '*' });
  app.use((req: Request, res: Response, next: NextFunction) => {
    const t = Date.now();
    res.on('finish', () => logger.log(`${req.method} ${req.originalUrl} ${res.statusCode} ${Date.now() - t}ms`));
    next();
  });
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      exceptionFactory: (errors) => new BadRequestException(flatten(errors)),
    }),
  );
  app.useGlobalFilters(new GlobalExceptionFilter());

  const config = new DocumentBuilder()
    .setTitle('EasySign Mobile API')
    .setDescription('API do app EasySign: documentos PDF, signatários, campos de assinatura (coordenadas normalizadas 0..1), envio, assinatura com metadados do gesto e trilha de auditoria SHA-256.')
    .setVersion('1.0')
    .addBearerAuth()
    .build();
  const doc = SwaggerModule.createDocument(app, config);
  const css = `
    body{background:#f7f7f9} .swagger-ui{font-family:Inter,system-ui,sans-serif;color:#1f2330}
    .swagger-ui .info .title{color:#c2185b} .swagger-ui .info{margin:32px 0}
    .swagger-ui .opblock{border-radius:8px;box-shadow:none}
    .swagger-ui .btn.authorize{color:#c2185b;border-color:#c2185b} .swagger-ui .btn.authorize svg{fill:#c2185b}`;
  for (const path of ['api', 'api-docs']) {
    app.use(`/${path}`, (_req: Request, res: Response, next: NextFunction) => {
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
      res.setHeader('Pragma', 'no-cache');
      res.setHeader('Expires', '0');
      res.setHeader('Surrogate-Control', 'no-store');
      next();
    });
    SwaggerModule.setup(path, app, doc, {
      customSiteTitle: 'EasySign Mobile API',
      customCss: css,
      customfavIcon: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 16 16%22%3E%3Crect width=%2216%22 height=%2216%22 rx=%223%22 fill=%22%23c2185b%22/%3E%3C/svg%3E',
      swaggerOptions: { layout: 'BaseLayout', persistAuthorization: true },
    });
  }
  await app.listen(3000);
  Logger.log('EasySign API rodando na porta 3000', 'Bootstrap');
}
bootstrap();

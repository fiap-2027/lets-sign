import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from './common/auth';
import { PrismaService } from './prisma.service';

@ApiTags('Saúde')
@Controller()
export class AppController {
  constructor(private prisma: PrismaService) {}

  @Public()
  @Get('health')
  @ApiOperation({ summary: 'Verifica se a API e o banco estão no ar' })
  async health() {
    let banco = 'ok';
    try {
      await this.prisma.$queryRaw`SELECT 1`;
    } catch {
      banco = 'indisponível';
    }
    return { status: banco === 'ok' ? 'ok' : 'degradado', banco, servico: 'EasySign Mobile API', timestamp: new Date().toISOString() };
  }
}

import { PrismaClient, Role } from '@prisma/client';
import * as bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  const admin = await prisma.user.upsert({
    where: { email: 'admin@letssign.com' },
    update: {},
    create: { nome: 'Admin Demo EasySign', email: 'admin@letssign.com', telefone: '+5511900000000', senhaHash: await bcrypt.hash('Admin@123', 10), role: Role.ADMIN },
  });
  const signers = [
    { nome: 'Ana Contratante', email: 'ana@letssign.com', telefone: '+5511911111111' },
    { nome: 'Bruno Contratado', email: 'bruno@letssign.com', telefone: '+5511922222222' },
  ];
  for (const s of signers) {
    await prisma.user.upsert({
      where: { email: s.email },
      update: {},
      create: { ...s, senhaHash: await bcrypt.hash('Signer@123', 10), role: Role.SIGNER, adminId: admin.id },
    });
  }
  console.log('Seed concluído');
}

main().finally(() => prisma.$disconnect());
